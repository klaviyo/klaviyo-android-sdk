package com.klaviyo.forms.bridge

import android.view.Gravity
import com.klaviyo.core.Registry
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Position options for floating forms
 */
internal enum class FormPosition {
    FULLSCREEN,
    TOP,
    BOTTOM,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER;

    /**
     * Convert form position to Android Gravity flags
     */
    fun toGravity(): Int = when (this) {
        FULLSCREEN -> Gravity.CENTER
        TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        TOP_LEFT -> Gravity.TOP or Gravity.START
        TOP_RIGHT -> Gravity.TOP or Gravity.END
        BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
        BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
        CENTER -> Gravity.CENTER
    }

    companion object {
        fun fromString(value: String?): FormPosition = when (value?.uppercase()) {
            "FULLSCREEN" -> FULLSCREEN
            "TOP" -> TOP
            "BOTTOM" -> BOTTOM
            "TOP_LEFT" -> TOP_LEFT
            "TOP_RIGHT" -> TOP_RIGHT
            "BOTTOM_LEFT" -> BOTTOM_LEFT
            "BOTTOM_RIGHT" -> BOTTOM_RIGHT
            "CENTER" -> CENTER
            else -> FULLSCREEN
        }
    }
}

/**
 * Unit type for dimensions
 */
internal enum class DimensionUnit {
    PERCENT,
    FIXED;

    companion object {
        fun fromString(value: String?): DimensionUnit = when (value?.uppercase()) {
            "PERCENT" -> PERCENT
            "FIXED" -> FIXED
            else -> FIXED
        }
    }
}

/**
 * Represents a dimension value with its unit type
 */
internal data class Dimension(
    val value: Float,
    val unit: DimensionUnit
) {
    /**
     * Convert dimension to pixels
     *
     * @param screenDimension The screen dimension (width or height) in pixels
     * @param density The screen density (pixels per dp)
     * @return The dimension in pixels
     */
    // TODO: Test on devices with different zoom states
    //  (system-wide settings for controlling content scaling)
    fun toPixels(screenDimension: Int, density: Float): Int = when (unit) {
        DimensionUnit.PERCENT -> (screenDimension * (value / 100f)).toInt()
        DimensionUnit.FIXED -> (value * density).toInt()
    }

    companion object {
        fun fromJson(json: JSONObject?): Dimension? {
            if (json == null) return null
            return Dimension(
                value = json.optDouble("value", 0.0).toFloat(),
                unit = DimensionUnit.fromString(json.optString("unit"))
            )
        }

        /**
         * Create a fixed dimension in dp
         */
        fun dp(value: Float): Dimension = Dimension(value, DimensionUnit.FIXED)

        /**
         * Create a percent dimension
         */
        fun percent(value: Float): Dimension = Dimension(value, DimensionUnit.PERCENT)
    }
}

/**
 * Offsets around the form in dp
 */
internal data class Offsets(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f
) {
    companion object {
        fun fromJson(json: JSONObject?): Offsets {
            if (json == null) return Offsets()
            return Offsets(
                top = json.optDouble("top", 0.0).toFloat(),
                bottom = json.optDouble("bottom", 0.0).toFloat(),
                left = json.optDouble("left", 0.0).toFloat(),
                right = json.optDouble("right", 0.0).toFloat()
            )
        }

        /**
         * Create uniform offsets
         */
        fun all(value: Float): Offsets = Offsets(value, value, value, value)
    }
}

/**
 * Complete layout configuration for a form
 */
internal data class FormLayout(
    val position: FormPosition,
    val width: Dimension,
    val height: Dimension,
    val offsets: Offsets = Offsets(),
    /**
     * When true (default), the SDK adds safe-area insets to the provided [offsets]
     * when positioning the form. When false, the SDK uses [offsets] as-is and does
     * not account for safe-area at all — onsite is fully responsible for baking
     * any safe-area inset it wants into [offsets].
     */
    val addSafeAreaInsetsToOffsets: Boolean = true
) {
    /**
     * Returns true if this layout represents a fullscreen form
     */
    val isFullscreen: Boolean
        get() = position == FormPosition.FULLSCREEN

    companion object {
        /**
         * Guards the one-time deprecation log emitted when a payload uses the legacy
         * `margin` wire key instead of the new `offsets` key. Scoped to the process
         * lifetime — the webview is re-created per session, so this effectively emits
         * at most once per webview session (and at most once per process, whichever
         * comes first).
         */
        private val loggedMarginDeprecation = AtomicBoolean(false)

        fun fromJson(json: JSONObject?): FormLayout? {
            if (json == null) return null

            val position = FormPosition.fromString(json.optString("position"))
            val width = Dimension.fromJson(json.optJSONObject("width")) ?: return null
            val height = Dimension.fromJson(json.optJSONObject("height")) ?: return null

            // Prefer the new `offsets` wire key; fall back to legacy `margin` for
            // backward compatibility with older onsite payloads. Log once per session
            // when we hit the fallback so we can track deprecation in the wild.
            val offsetsJson = json.optJSONObject("offsets") ?: json.optJSONObject("margin")?.also {
                if (loggedMarginDeprecation.compareAndSet(false, true)) {
                    Registry.log.verbose(
                        "FormLayout payload used deprecated `margin` key; " +
                            "expected `offsets`. Update onsite to emit `offsets`."
                    )
                }
            }
            val offsets = Offsets.fromJson(offsetsJson)
            val addSafeAreaInsetsToOffsets = json.optBoolean("addSafeAreaInsetsToOffsets", true)

            return FormLayout(
                position = position,
                width = width,
                height = height,
                offsets = offsets,
                addSafeAreaInsetsToOffsets = addSafeAreaInsetsToOffsets
            )
        }
    }
}
