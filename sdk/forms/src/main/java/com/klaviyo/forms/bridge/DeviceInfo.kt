package com.klaviyo.forms.bridge

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.view.Surface
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import com.klaviyo.core.Registry
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Describes the device's current physical display characteristics, exposed to onsite JS
 * via the `data-klaviyo-device` attribute on the HTML `<head>` element.
 *
 * The shape intentionally mirrors CSSOM conventions so onsite code can treat the payload
 * as a reliable, orientation-aware substitute for `window.screen.*` during the synchronous
 * HTML parse phase, before the webview attaches to the view hierarchy.
 */
internal data class DeviceInfo(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val insetTopDp: Int,
    val insetBottomDp: Int,
    val insetLeftDp: Int,
    val insetRightDp: Int,
    val orientation: Orientation,
    val dpr: Int
) {
    /**
     * Serializes the device info into its JSON representation as published on the
     * `data-klaviyo-device` head attribute.
     */
    fun toJson(): String = JSONObject()
        .put(
            KEY_SCREEN,
            JSONObject()
                .put(KEY_WIDTH, screenWidthDp)
                .put(KEY_HEIGHT, screenHeightDp)
        )
        .put(
            KEY_SAFE_AREA_INSETS,
            JSONObject()
                .put(KEY_TOP, insetTopDp)
                .put(KEY_BOTTOM, insetBottomDp)
                .put(KEY_LEFT, insetLeftDp)
                .put(KEY_RIGHT, insetRightDp)
        )
        .put(KEY_ORIENTATION, orientation.cssValue)
        .put(KEY_DPR, dpr)
        .toString()

    /**
     * CSSOM `ScreenOrientation.type` vocabulary.
     * See https://drafts.csswg.org/screen-orientation/#enumdef-orientationtype
     */
    internal enum class Orientation(val cssValue: String) {
        PortraitPrimary("portrait-primary"),
        PortraitSecondary("portrait-secondary"),
        LandscapePrimary("landscape-primary"),
        LandscapeSecondary("landscape-secondary");

        companion object {
            /**
             * Derives the CSSOM orientation label from the Android [Configuration.orientation]
             * and [android.view.Display.getRotation] values.
             *
             * Android's rotation values describe the counter-clockwise rotation applied to the
             * natural orientation; pairing that with whether the logical orientation is portrait
             * or landscape gives us enough to pick the `*-primary` vs `*-secondary` flavor.
             *
             * Caveat: this mapping assumes a natural-portrait device, which holds for all phones
             * and is sufficient for our product scope. On natural-landscape devices (some tablets
             * and foldables) the rotation-to-CSSOM mapping may diverge from
             * [`screen.orientation.type`](https://drafts.csswg.org/screen-orientation/#dom-screenorientation-type):
             * for example, `rotation = 90` on a natural-landscape device is reported here as
             * `portrait-secondary`, where the web platform would report `portrait-primary`.
             */
            fun from(configOrientation: Int, rotation: Int): Orientation {
                val isPortrait = configOrientation != Configuration.ORIENTATION_LANDSCAPE
                return when (rotation) {
                    Surface.ROTATION_0 -> if (isPortrait) PortraitPrimary else LandscapePrimary
                    Surface.ROTATION_90 -> if (isPortrait) PortraitSecondary else LandscapePrimary
                    Surface.ROTATION_180 -> if (isPortrait) PortraitSecondary else LandscapeSecondary
                    Surface.ROTATION_270 -> if (isPortrait) PortraitPrimary else LandscapeSecondary
                    else -> if (isPortrait) PortraitPrimary else LandscapePrimary
                }
            }
        }
    }

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_SAFE_AREA_INSETS = "safeAreaInsets"
        private const val KEY_TOP = "top"
        private const val KEY_BOTTOM = "bottom"
        private const val KEY_LEFT = "left"
        private const val KEY_RIGHT = "right"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_DPR = "dpr"

        /**
         * Build a [DeviceInfo] snapshot from the given display metrics and insets.
         *
         * Separating computation from Android platform lookups keeps the logic pure and
         * testable. See [DeviceInfoProvider] for the live-device lookup entry point.
         */
        fun from(
            displayMetrics: DisplayMetrics,
            configuration: Configuration,
            rotation: Int,
            insetLeftPx: Int,
            insetTopPx: Int,
            insetRightPx: Int,
            insetBottomPx: Int
        ): DeviceInfo {
            // Guard against pathological DisplayMetrics (density <= 0) which would cause
            // NaN from pxToDp. This most commonly occurs in test doubles.
            val safeDensity = displayMetrics.density.takeIf { it > 0f } ?: 1f
            fun pxToDpRounded(px: Int): Int = (px / safeDensity).roundToInt()
            return DeviceInfo(
                screenWidthDp = pxToDpRounded(displayMetrics.widthPixels),
                screenHeightDp = pxToDpRounded(displayMetrics.heightPixels),
                insetTopDp = pxToDpRounded(insetTopPx),
                insetBottomDp = pxToDpRounded(insetBottomPx),
                insetLeftDp = pxToDpRounded(insetLeftPx),
                insetRightDp = pxToDpRounded(insetRightPx),
                orientation = Orientation.from(configuration.orientation, rotation),
                dpr = safeDensity.roundToInt().coerceAtLeast(1)
            )
        }
    }
}

/**
 * Live-device lookup for [DeviceInfo]. Reads from the currently tracked activity when available,
 * falling back to the system-wide resources so early callers (before activity attachment) still
 * produce a reasonable snapshot.
 */
internal object DeviceInfoProvider {

    /**
     * Snapshot the current device state. Prefers values from the tracked activity so that
     * safe-area insets and rotation reflect the actual window placement rather than the raw
     * display.
     */
    fun current(): DeviceInfo {
        val activity = Registry.lifecycleMonitor.currentActivity
        val resources = activity?.resources ?: Resources.getSystem()
        val displayMetrics = resources.displayMetrics
        val configuration = resources.configuration

        val rotation = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.display
            } else {
                @Suppress("DEPRECATION")
                activity?.windowManager?.defaultDisplay
            }?.rotation ?: Surface.ROTATION_0
        }.getOrDefault(Surface.ROTATION_0)

        data class InsetsPx(val left: Int, val top: Int, val right: Int, val bottom: Int)
        val insetsPx = runCatching {
            activity?.window?.decorView?.rootWindowInsets?.let { raw ->
                val compat = WindowInsetsCompat.toWindowInsetsCompat(raw)
                val insets = compat.getInsets(systemBars() or displayCutout())
                InsetsPx(insets.left, insets.top, insets.right, insets.bottom)
            }
        }.getOrNull() ?: InsetsPx(0, 0, 0, 0)

        return DeviceInfo.from(
            displayMetrics = displayMetrics,
            configuration = configuration,
            rotation = rotation,
            insetLeftPx = insetsPx.left,
            insetTopPx = insetsPx.top,
            insetRightPx = insetsPx.right,
            insetBottomPx = insetsPx.bottom
        )
    }
}

/**
 * Escapes a JSON payload for embedding inside a single-quoted JS string literal.
 *
 * Only backslashes and single quotes need escaping — JSON is otherwise JS-safe because
 * [JSONObject] already escapes double quotes, control characters, and non-ASCII characters.
 */
internal fun String.jsEscape(): String = this
    .replace("\\", "\\\\")
    .replace("'", "\\'")
