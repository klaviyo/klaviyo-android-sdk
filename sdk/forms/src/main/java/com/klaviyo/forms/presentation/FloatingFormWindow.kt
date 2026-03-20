package com.klaviyo.forms.presentation

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
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

            // Calculate offsets based on margins and position
            x = calculateHorizontalOffset(layout, density)
            y = calculateVerticalOffset(layout, density)

            // FLAG_NOT_TOUCH_MODAL: Allow touches outside the window to pass through
            // FLAG_LAYOUT_NO_LIMITS: Allow window to extend beyond screen bounds for positioning
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            format = PixelFormat.TRANSLUCENT
        }

        windowParams = params

        Registry.threadHelper.runOnUiThread {
            try {
                val newContainer = FrameLayout(hostActivity).apply {
                    // TODO: Remove debug background before production
                    setBackgroundColor(DEBUG_BACKGROUND_COLOR)
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
                Registry.log.debug("FloatingFormWindow shown at ${layout.position}")
            } catch (e: Exception) {
                Registry.log.error("Failed to show FloatingFormWindow", e)
                container = null
                windowParams = null
            }
        }
    }

    /**
     * Dismiss the floating form window and remove it from the WindowManager
     */
    fun dismiss() {
        Registry.threadHelper.runOnUiThread {
            container?.let { view ->
                try {
                    windowManager.removeView(view)
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
     * Update the layout of the floating form window
     *
     * TODO: Will be used for dynamic resizing (e.g. after orientation change)
     *
     * @param layout The new layout configuration
     */
    fun updateLayout(layout: FormLayout) {
        val params = windowParams ?: return
        val container = container ?: return

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        params.width = layout.width.toPixels(screenWidth, density)
        params.height = layout.height.toPixels(screenHeight, density)
        params.gravity = layout.position.toGravity()
        params.x = calculateHorizontalOffset(layout, density)
        params.y = calculateVerticalOffset(layout, density)

        Registry.threadHelper.runOnUiThread {
            try {
                windowManager.updateViewLayout(container, params)
                Registry.log.debug("FloatingFormWindow layout updated")
            } catch (e: Exception) {
                Registry.log.error("Failed to update FloatingFormWindow layout", e)
            }
        }
    }

    /**
     * Calculate horizontal offset based on position and margins
     */
    private fun calculateHorizontalOffset(layout: FormLayout, density: Float): Int {
        val leftMargin = (layout.margins.left * density).toInt()
        val rightMargin = (layout.margins.right * density).toInt()

        return when (layout.position) {
            FormPosition.TOP_LEFT, FormPosition.BOTTOM_LEFT -> leftMargin
            FormPosition.TOP_RIGHT, FormPosition.BOTTOM_RIGHT -> -rightMargin
            FormPosition.TOP, FormPosition.BOTTOM, FormPosition.CENTER, FormPosition.FULLSCREEN -> 0
        }
    }

    /**
     * Calculate vertical offset based on position and margins
     */
    private fun calculateVerticalOffset(layout: FormLayout, density: Float): Int {
        val topMargin = (layout.margins.top * density).toInt()
        val bottomMargin = (layout.margins.bottom * density).toInt()

        return when (layout.position) {
            FormPosition.TOP, FormPosition.TOP_LEFT, FormPosition.TOP_RIGHT -> topMargin
            FormPosition.BOTTOM, FormPosition.BOTTOM_LEFT, FormPosition.BOTTOM_RIGHT -> -bottomMargin
            FormPosition.CENTER, FormPosition.FULLSCREEN -> 0
        }
    }

    companion object {
        /**
         * Semi-transparent red for debug visibility
         */
        private const val DEBUG_BACKGROUND_COLOR = 0x30FF0000
    }
}
