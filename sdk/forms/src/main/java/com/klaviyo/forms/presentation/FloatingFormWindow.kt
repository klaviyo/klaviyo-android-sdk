package com.klaviyo.forms.presentation

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import com.klaviyo.core.Registry
import com.klaviyo.forms.bridge.FormLayout
import com.klaviyo.forms.bridge.FormPosition

/**
 * Manages displaying a floating form using WindowManager overlays.
 *
 * This approach uses TYPE_APPLICATION_PANEL windows attached to the host activity's window token,
 * allowing forms to appear as floating panels without creating a separate activity.
 * The host activity remains fully interactive, including keyboard input.
 */
internal class FloatingFormWindow(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var container: FrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var originalYOffset: Int = 0

    /**
     * The form's bottom edge distance from the screen bottom (in pixels),
     * computed from gravity, offset, and form height. Used to determine
     * whether the keyboard overlaps the form and by how much.
     */
    private var formBottomGap: Int = 0

    /**
     * Whether the form uses bottom-anchored gravity (BOTTOM, BOTTOM_LEFT, BOTTOM_RIGHT).
     * Determines the direction of keyboard shift — bottom-anchored forms shift with
     * positive y (up from bottom), others shift with negative y (up from anchor).
     */
    private var isBottomAnchored: Boolean = false

    /**
     * Keyboard animation tracking for the host activity's root view.
     * Since the floating window uses FLAG_NOT_FOCUSABLE, inset callbacks won't fire
     * on the window itself — we must observe from the host activity's view hierarchy.
     *
     * Uses WindowInsetsAnimationCompat to smoothly track the keyboard animation
     * frame-by-frame, shifting the flyout in sync with the keyboard slide.
     * On API < 30, animation callbacks don't fire so we fall back to
     * OnApplyWindowInsetsListener for an instant (non-animated) shift.
     *
     * We shift the flyout above the keyboard rather than hiding it so the user
     * can still see the form while typing in the host app.
     * An alternative approach is to hide the flyout entirely (set container GONE)
     * which is simpler and avoids edge cases with tall forms that don't fit
     * above the keyboard, but means the user loses sight of the form while typing.
     */
    private var hostRootView: View? = null
    private var isAnimatingKeyboard = false

    /**
     * Show the floating form window with the given webView and layout configuration
     *
     * @param hostActivity The activity to attach the window to (provides window token)
     * @param webView The WebView to display in the floating window
     * @param layout The layout configuration for positioning and sizing
     */
    fun show(hostActivity: Activity, webView: View, layout: FormLayout) {
        if (container != null) {
            Registry.log.warning("FloatingFormWindow already shown, dismissing first")
            dismiss()
        }

        val displayMetrics = DisplayMetrics()
        // TODO: Use WindowMetrics when minSdk >= 30
        @Suppress("DEPRECATION")
        hostActivity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        // Read safe area insets (notch, system bars, display cutouts) from the
        // host activity's decor view. These ensure the form is not obscured by
        // device hardware or system UI.
        val rootWindowInsets = ViewCompat.getRootWindowInsets(hostActivity.window.decorView)
        val safeInsets = rootWindowInsets?.getInsets(systemBars() or displayCutout())
        val safeAreaTop = safeInsets?.top ?: 0
        val safeAreaBottom = safeInsets?.bottom ?: 0
        val safeAreaLeft = safeInsets?.left ?: 0
        val safeAreaRight = safeInsets?.right ?: 0

        val params = WindowManager.LayoutParams().apply {
            // TYPE_APPLICATION_PANEL (1000) doesn't require special permissions
            // It attaches as a panel to the parent window
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL

            // Get the window token from the host activity's decor view
            token = hostActivity.window.decorView.applicationWindowToken

            // Calculate dimensions
            width = layout.width.toPixels(screenWidth, density)
            height = layout.height.toPixels(screenHeight, density)

            // Set gravity based on position
            gravity = layout.position.toGravity()

            // Calculate offsets based on position, including safe area insets
            x = calculateHorizontalOffset(layout, density, safeAreaLeft, safeAreaRight)
            y = calculateVerticalOffset(layout, density, safeAreaTop, safeAreaBottom)

            // FLAG_NOT_TOUCH_MODAL: Allow touches outside the window to pass through
            // FLAG_NOT_FOCUSABLE: Let the host activity retain input focus so its keyboard
            //   still works while the flyout is visible. Flyout forms currently only need
            //   tap/scroll interaction (buttons, links), not text input.
            //   TODO: When flyout forms support text input (email collection, etc.), this flag
            //    will need to be removed or toggled dynamically so the WebView can receive
            //    keyboard focus. Consider removing FLAG_NOT_FOCUSABLE when the user taps a
            //    form input and restoring it when the input loses focus.
            // FLAG_LAYOUT_NO_LIMITS: Allow window to extend beyond screen bounds for positioning
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            format = PixelFormat.TRANSLUCENT
        }

        originalYOffset = params.y
        formBottomGap = calculateFormBottomGap(
            layout,
            params.height,
            screenHeight,
            density,
            safeAreaTop,
            safeAreaBottom
        )
        isBottomAnchored = layout.position in listOf(
            FormPosition.BOTTOM,
            FormPosition.BOTTOM_LEFT,
            FormPosition.BOTTOM_RIGHT
        )
        windowParams = params

        Registry.threadHelper.runOnUiThread {
            try {
                val newContainer = FrameLayout(hostActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                // Detach webView from any existing parent before adding
                (webView.parent as? ViewGroup)?.removeView(webView)
                // Restore visibility in case it was hidden by detachWebView()
                webView.visibility = View.VISIBLE
                newContainer.addView(
                    webView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                container = newContainer
                windowManager.addView(newContainer, params)
                startKeyboardMonitor(hostActivity)
                Registry.log.debug("FloatingFormWindow shown at ${layout.position}")
            } catch (e: Exception) {
                Registry.log.error("Failed to show FloatingFormWindow", e)
                container = null
                windowParams = null
            }
        }
    }

    /**
     * Dismiss the floating form window and remove it from the WindowManager.
     *
     * Uses [WindowManager.removeViewImmediate] to force synchronous view detach.
     * This prevents WindowLeaked errors during activity destruction (e.g. rotation),
     * where [WindowManager.removeView] would post the detach asynchronously and
     * the activity could be destroyed before the view is actually removed.
     */
    fun dismiss() {
        Registry.threadHelper.runOnUiThread {
            stopKeyboardMonitor()
            container?.let { view ->
                try {
                    windowManager.removeViewImmediate(view)
                    Registry.log.debug("FloatingFormWindow dismissed")
                } catch (e: Exception) {
                    Registry.log.error("Failed to dismiss FloatingFormWindow", e)
                }
            }
            container = null
            windowParams = null
        }
    }

    /**
     * Start monitoring keyboard animation from the host activity's root view.
     *
     * Uses [WindowInsetsAnimationCompat.Callback] to track the keyboard frame-by-frame
     * on API 30+, shifting the flyout smoothly in sync. Falls back to
     * [ViewCompat.setOnApplyWindowInsetsListener] for an instant shift on older APIs
     * where animation callbacks don't fire.
     */
    private fun startKeyboardMonitor(hostActivity: Activity) {
        val rootView = hostActivity.window.decorView.rootView
        hostRootView = rootView

        // Animation callback: tracks keyboard slide frame-by-frame (API 30+)
        val animationCallback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                applyKeyboardShift(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                return insets
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat
            ): WindowInsetsAnimationCompat.BoundsCompat {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    isAnimatingKeyboard = true
                }
                return bounds
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                    isAnimatingKeyboard = false
                }
            }
        }

        ViewCompat.setWindowInsetsAnimationCallback(rootView, animationCallback)

        // Fallback insets listener for API < 30 where animation callbacks don't fire.
        // On API 30+, the animation callback handles positioning so we skip the
        // instant update here to avoid fighting with the animation.
        val insetsListener = OnApplyWindowInsetsListener { _, insets ->
            if (!isAnimatingKeyboard) {
                applyKeyboardShift(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView, insetsListener)
    }

    /**
     * Stop monitoring keyboard and clean up listeners
     */
    private fun stopKeyboardMonitor() {
        hostRootView?.let {
            ViewCompat.setWindowInsetsAnimationCallback(it, null)
            ViewCompat.setOnApplyWindowInsetsListener(it, null)
        }
        hostRootView = null
        isAnimatingKeyboard = false
    }

    /**
     * Shift the flyout up only if the keyboard actually overlaps the form.
     * Computes the overlap between the keyboard top edge and the form bottom edge,
     * and shifts by exactly the overlap amount — no shift if the form is fully above
     * the keyboard (e.g. top-aligned short forms).
     *
     * The shift direction depends on gravity:
     * - BOTTOM gravity: positive y = up, so we ADD the overlap
     * - TOP/CENTER gravity: positive y = down, so we SUBTRACT the overlap
     *
     * @param keyboardHeight Current keyboard height in pixels (0 when closed)
     */
    private fun applyKeyboardShift(keyboardHeight: Int) {
        val params = windowParams ?: return
        val currentContainer = container ?: return

        // How far the keyboard intrudes into the form's space
        val overlap = (keyboardHeight - formBottomGap).coerceAtLeast(0)

        // Shift direction depends on gravity anchor:
        // Bottom-anchored: positive y moves up from bottom edge
        // Top/Center-anchored: negative y moves up from anchor point
        params.y = if (isBottomAnchored) {
            originalYOffset + overlap
        } else {
            originalYOffset - overlap
        }

        try {
            windowManager.updateViewLayout(currentContainer, params)
        } catch (e: Exception) {
            Registry.log.error("Failed to update flyout position for keyboard", e)
        }
    }

    /**
     * Calculate the gap between the form's bottom edge and the screen bottom (in pixels).
     * Used to determine keyboard overlap — if keyboardHeight > formBottomGap, the keyboard
     * overlaps the form and we need to shift.
     *
     * For BOTTOM gravity: gap = vertical offset (safe area + user offset)
     * For TOP gravity: gap = screenHeight - totalTopOffset - formHeight
     * For CENTER gravity: gap = (screenHeight - formHeight) / 2
     */
    private fun calculateFormBottomGap(
        layout: FormLayout,
        formHeight: Int,
        screenHeight: Int,
        density: Float,
        safeAreaTop: Int,
        safeAreaBottom: Int
    ): Int {
        val topOffset = (layout.offsets.top * density).toInt()
        val bottomOffset = (layout.offsets.bottom * density).toInt()

        return when (layout.position) {
            FormPosition.BOTTOM,
            FormPosition.BOTTOM_LEFT,
            FormPosition.BOTTOM_RIGHT -> safeAreaBottom + bottomOffset

            FormPosition.TOP,
            FormPosition.TOP_LEFT,
            FormPosition.TOP_RIGHT -> screenHeight - safeAreaTop - topOffset - formHeight

            FormPosition.CENTER -> (screenHeight - formHeight) / 2

            FormPosition.FULLSCREEN -> 0
        }
    }

    /**
     * Calculate horizontal offset based on position, user offsets, and safe area insets.
     * Safe area insets ensure the form clears notches, display cutouts, and system UI.
     * User offsets are additive on top of safe area.
     *
     * @param safeAreaLeft Left safe area inset in pixels
     * @param safeAreaRight Right safe area inset in pixels
     */
    private fun calculateHorizontalOffset(
        layout: FormLayout,
        density: Float,
        safeAreaLeft: Int,
        safeAreaRight: Int
    ): Int {
        val leftOffset = (layout.offsets.left * density).toInt()
        val rightOffset = (layout.offsets.right * density).toInt()

        return when (layout.position) {
            FormPosition.TOP_LEFT, FormPosition.BOTTOM_LEFT -> safeAreaLeft + leftOffset
            FormPosition.TOP_RIGHT, FormPosition.BOTTOM_RIGHT -> -(safeAreaRight + rightOffset)
            FormPosition.TOP, FormPosition.BOTTOM, FormPosition.CENTER, FormPosition.FULLSCREEN -> 0
        }
    }

    /**
     * Calculate vertical offset based on position, user offsets, and safe area insets.
     * Safe area insets ensure the form clears notches, Dynamic Island, and system UI.
     * User offsets are additive on top of safe area.
     *
     * @param safeAreaTop Top safe area inset in pixels
     * @param safeAreaBottom Bottom safe area inset in pixels
     */
    private fun calculateVerticalOffset(
        layout: FormLayout,
        density: Float,
        safeAreaTop: Int,
        safeAreaBottom: Int
    ): Int {
        val topOffset = (layout.offsets.top * density).toInt()
        val bottomOffset = (layout.offsets.bottom * density).toInt()

        return when (layout.position) {
            FormPosition.TOP, FormPosition.TOP_LEFT, FormPosition.TOP_RIGHT ->
                safeAreaTop + topOffset
            FormPosition.BOTTOM, FormPosition.BOTTOM_LEFT, FormPosition.BOTTOM_RIGHT ->
                safeAreaBottom + bottomOffset
            FormPosition.CENTER, FormPosition.FULLSCREEN -> 0
        }
    }
}
