package com.klaviyo.forms.presentation

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
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

    @Volatile
    private var isDismissed: Boolean = false

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
     * Keyboard observation for the host activity's view hierarchy.
     * Since the floating window uses FLAG_NOT_FOCUSABLE, IME insets are delivered to
     * the host activity's window — not ours — so we must observe from there.
     *
     * Strategy: inject a zero-size probe view as a direct child of the host DecorView
     * and attach our WindowInsetsAnimationCallback to it. This avoids clobbering any
     * callback the host app may have registered on its own root view, since
     * DISPATCH_MODE_STOP on a sibling only stops recursion into that sibling's subtree,
     * not dispatch to our probe view.
     *
     * Fallback: ViewTreeObserver.OnGlobalLayoutListener on the DecorView for pre-API 30
     * (where the compat animation shim may not reach a non-root child) and for the rare
     * case where an ancestor uses DISPATCH_MODE_STOP and blocks our animation callback.
     * The layout listener fires once per layout pass (no per-frame progress), so the
     * flyout snaps to its shifted position rather than tracking the keyboard slide.
     * The isAnimatingKeyboard guard prevents the two paths from fighting each other.
     */
    private var probeView: View? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var viewTreeObserver: ViewTreeObserver? = null
    private var isAnimatingKeyboard = false
    private var lastAppliedKeyboardHeight: Int = 0

    /**
     * Show the floating form window with the given webView and layout configuration
     *
     * @param hostActivity The activity to attach the window to (provides window token)
     * @param webView The WebView to display in the floating window
     * @param layout The layout configuration for positioning and sizing
     * @param onPresented Callback invoked after the window is successfully added to the screen
     * @param onError Callback invoked if addView fails, so the caller can reset state
     */
    fun show(
        hostActivity: Activity,
        webView: View,
        layout: FormLayout,
        onPresented: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        if (container != null) {
            Registry.log.warning("FloatingFormWindow already shown, dismissing first")
            dismiss()
        }
        // Reset in case this instance was previously dismissed (e.g. the dismiss() above,
        // or a rotation-triggered dismiss followed by re-presentation on the same instance)
        isDismissed = false

        val screenWidth: Int
        val screenHeight: Int
        val density: Float

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = hostActivity.windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            density = hostActivity.resources.displayMetrics.density
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            hostActivity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            density = displayMetrics.density
        }

        // Read safe area insets (notch, system bars, display cutouts) from the
        // host activity's decor view. These ensure the form is not obscured by
        // device hardware or system UI.
        val rootWindowInsets = ViewCompat.getRootWindowInsets(hostActivity.window.decorView)
        val safeInsets = rootWindowInsets?.getInsets(systemBars() or displayCutout())
        val safeAreaTop = safeInsets?.top ?: 0
        val safeAreaBottom = safeInsets?.bottom ?: 0
        val safeAreaLeft = safeInsets?.left ?: 0
        val safeAreaRight = safeInsets?.right ?: 0

        // Compute clamped size and gravity-relative offsets in a single pass so
        // width/height/x/y stay consistent with the offset-priority model (see
        // [calculateLayoutParams]). FULLSCREEN short-circuits to full bounds.
        val computed = calculateLayoutParams(
            layout = layout,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            safeAreaTop = safeAreaTop,
            safeAreaBottom = safeAreaBottom,
            safeAreaLeft = safeAreaLeft,
            safeAreaRight = safeAreaRight
        )

        val params = WindowManager.LayoutParams().apply {
            // TYPE_APPLICATION_PANEL (1000) doesn't require special permissions
            // It attaches as a panel to the parent window
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL

            // Get the window token from the host activity's decor view
            token = hostActivity.window.decorView.applicationWindowToken

            width = computed.width
            height = computed.height

            // Set gravity based on position
            gravity = layout.position.toGravity()

            // Gravity-relative offsets (Android's x/y on LayoutParams are offsets
            // from the gravity anchor, not absolute coordinates).
            x = computed.x
            y = computed.y

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
        Registry.log.verbose(
            "FloatingFormWindow layout: pos=${layout.position} size=${params.width}x${params.height} " +
                "offset=(${params.x},${params.y}) bottomGap=$formBottomGap"
        )
        isBottomAnchored = layout.position in listOf(
            FormPosition.BOTTOM,
            FormPosition.BOTTOM_LEFT,
            FormPosition.BOTTOM_RIGHT
        )
        windowParams = params

        Registry.threadHelper.runOnUiThread {
            // Guard: dismiss() may have been called between show() and this queued lambda.
            // If so, skip adding the view entirely to avoid an orphaned window and
            // leaked keyboard monitor that no subsequent dismiss() can reach.
            // Do NOT call onError here — dismiss already cleaned up the manager's state,
            // and a new present() may have set up fresh state that onError would corrupt.
            if (isDismissed) return@runOnUiThread

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
                onPresented?.invoke()
                startKeyboardMonitor(hostActivity)
                Registry.log.debug("FloatingFormWindow shown at ${layout.position}")
            } catch (e: Exception) {
                Registry.log.error("Failed to show FloatingFormWindow", e)
                // Clean up keyboard monitor in case startKeyboardMonitor partially
                // succeeded (e.g. injected probe view) before throwing
                stopKeyboardMonitor()
                // If addView succeeded before the throw, remove the leaked view
                container?.let {
                    try {
                        windowManager.removeViewImmediate(it)
                    } catch (removeEx: Exception) {
                        Registry.log.error("Failed to clean up leaked view", removeEx)
                    }
                }
                container = null
                windowParams = null
                onError?.invoke()
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
        isDismissed = true
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
     * Start monitoring keyboard animation from the host activity's view hierarchy.
     *
     * Injects a zero-size probe [View] as a direct child of the host [DecorView][android.view.Window.getDecorView]
     * and attaches a [WindowInsetsAnimationCompat.Callback] to it. This avoids clobbering any
     * animation callback the host app may have registered on its own root view.
     *
     * Falls back to [ViewTreeObserver.OnGlobalLayoutListener] for pre-API 30 devices or when
     * [WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP] on an ancestor blocks our
     * animation callback. The layout listener snaps the flyout to its final position rather
     * than tracking the keyboard slide frame-by-frame. The [isAnimatingKeyboard] flag prevents
     * both paths from applying a shift at the same time.
     */
    private fun startKeyboardMonitor(hostActivity: Activity) {
        // Guard against double-registration if show() is called while already monitoring
        stopKeyboardMonitor()

        val decorView = hostActivity.window.decorView as ViewGroup
        lastAppliedKeyboardHeight = 0

        // Animation callback: tracks keyboard slide frame-by-frame (API 30+)
        val animationCallback = object : WindowInsetsAnimationCompat.Callback(
            DISPATCH_MODE_CONTINUE_ON_SUBTREE
        ) {
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

        // Inject a zero-size probe view as a direct sibling of the app's content inside DecorView.
        // Attaching the callback here avoids replacing whatever the host app set on its root view.
        probeView = View(hostActivity).also { probe ->
            decorView.addView(probe, ViewGroup.LayoutParams(0, 0))
            ViewCompat.setWindowInsetsAnimationCallback(probe, animationCallback)
        }

        // Fallback: fires once per layout pass when keyboard height changes.
        // Guards with isAnimatingKeyboard so it doesn't fight the per-frame animation path.
        // Uses ViewCompat.getRootWindowInsets with WindowInsetsCompat.Type.ime() to isolate
        // only the keyboard height — avoids the nav bar contamination of getWindowVisibleDisplayFrame.
        // If an ancestor in the host app uses DISPATCH_MODE_STOP and blocks the animation callback,
        // this ensures the flyout still shifts — snapping to the final position rather than
        // tracking the keyboard slide frame-by-frame.
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!isAnimatingKeyboard) {
                val probe = probeView ?: return@OnGlobalLayoutListener
                val keyboardHeight = ViewCompat.getRootWindowInsets(probe)
                    ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                if (keyboardHeight != lastAppliedKeyboardHeight) {
                    lastAppliedKeyboardHeight = keyboardHeight
                    applyKeyboardShift(keyboardHeight)
                }
            }
        }.also { layoutListener ->
            decorView.viewTreeObserver.also { vto ->
                viewTreeObserver = vto
                vto.addOnGlobalLayoutListener(layoutListener)
            }
        }
    }

    /**
     * Stop monitoring keyboard and clean up the probe view and listeners.
     */
    private fun stopKeyboardMonitor() {
        probeView?.let { probe ->
            ViewCompat.setWindowInsetsAnimationCallback(probe, null)
            (probe.parent as? ViewGroup)?.removeView(probe)
        }

        viewTreeObserver?.let { vto ->
            if (vto.isAlive) {
                globalLayoutListener?.let {
                    vto.removeOnGlobalLayoutListener(it)
                }
            }
        }

        probeView = null
        viewTreeObserver = null
        globalLayoutListener = null
        isAnimatingKeyboard = false
        lastAppliedKeyboardHeight = 0
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
     * Fully resolved [WindowManager.LayoutParams] dimensions for a floating form:
     * clamped [width] / [height] (after honoring safe-area + user margins) and
     * gravity-relative [x] / [y] offsets.
     */
    internal data class ComputedLayout(
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int
    )

    companion object {
        /**
         * Compute the clamped form size and gravity-relative x / y offsets for a given
         * [FormLayout], mirroring the iOS `calculateFrame` behavior:
         *
         * Margins are the sum of safe-area insets and user-provided offsets on each edge,
         * and they take priority over the requested form dimensions — the form shrinks to
         * fit inside `screen - margins` when it would otherwise overflow. When the form
         * already fits, margins still anchor it away from the edge but do not shrink it.
         *
         * FULLSCREEN ignores offsets and fills the full screen bounds.
         *
         * For centered axes with asymmetric margins, the form shifts toward the smaller
         * margin so it remains visually centered within the available space (matches iOS).
         */
        internal fun calculateLayoutParams(
            layout: FormLayout,
            screenWidth: Int,
            screenHeight: Int,
            density: Float,
            safeAreaTop: Int,
            safeAreaBottom: Int,
            safeAreaLeft: Int,
            safeAreaRight: Int
        ): ComputedLayout {
            if (layout.position == FormPosition.FULLSCREEN) {
                return ComputedLayout(width = screenWidth, height = screenHeight, x = 0, y = 0)
            }

            // When addSafeAreaInsetsToOffsets=false, onsite owns safe-area handling end-to-end:
            // offsets are treated as the absolute distance from the screen edge and the
            // SDK does not touch safe-area at all (position math and clamping both skip
            // the safe-area term). Default (true) preserves the historical behavior of
            // SDK-managed safe-area insets.
            val effectiveSafeTop = if (layout.addSafeAreaInsetsToOffsets) safeAreaTop else 0
            val effectiveSafeBottom = if (layout.addSafeAreaInsetsToOffsets) safeAreaBottom else 0
            val effectiveSafeLeft = if (layout.addSafeAreaInsetsToOffsets) safeAreaLeft else 0
            val effectiveSafeRight = if (layout.addSafeAreaInsetsToOffsets) safeAreaRight else 0

            val offsetTop = effectiveSafeTop + (layout.offsets.top * density).toInt()
            val offsetBottom = effectiveSafeBottom + (layout.offsets.bottom * density).toInt()
            val offsetLeft = effectiveSafeLeft + (layout.offsets.left * density).toInt()
            val offsetRight = effectiveSafeRight + (layout.offsets.right * density).toInt()

            val availableWidth = (screenWidth - offsetLeft - offsetRight).coerceAtLeast(0)
            val availableHeight = (screenHeight - offsetTop - offsetBottom).coerceAtLeast(0)

            val requestedWidth = layout.width.toPixels(screenWidth, density)
            val requestedHeight = layout.height.toPixels(screenHeight, density)
            val clampedWidth = minOf(requestedWidth, availableWidth)
            val clampedHeight = minOf(requestedHeight, availableHeight)

            // Android gravity places the clamped view anchored to the gravity edge; our
            // x/y are additive offsets from that anchor. Cornered anchors take the full
            // margin; centered anchors use half the margin asymmetry so the form is
            // centered within [marginStart, screenDim - marginEnd] instead of the raw
            // screen bounds.
            val x = when (layout.position) {
                FormPosition.TOP_LEFT, FormPosition.BOTTOM_LEFT -> offsetLeft
                FormPosition.TOP_RIGHT, FormPosition.BOTTOM_RIGHT -> offsetRight
                FormPosition.TOP, FormPosition.BOTTOM, FormPosition.CENTER -> (offsetLeft - offsetRight) / 2
                FormPosition.FULLSCREEN -> 0
            }
            val y = when (layout.position) {
                FormPosition.TOP, FormPosition.TOP_LEFT, FormPosition.TOP_RIGHT -> offsetTop
                FormPosition.BOTTOM, FormPosition.BOTTOM_LEFT, FormPosition.BOTTOM_RIGHT -> offsetBottom
                FormPosition.CENTER -> (offsetTop - offsetBottom) / 2
                FormPosition.FULLSCREEN -> 0
            }

            return ComputedLayout(
                width = clampedWidth,
                height = clampedHeight,
                x = x,
                y = y
            )
        }

        /**
         * Calculate the gap between the form's bottom edge and the screen bottom (in pixels).
         * Used to determine keyboard overlap — if keyboardHeight > formBottomGap, the keyboard
         * overlaps the form and we need to shift. [formHeight] is the CLAMPED pixel height
         * already stored on the window params (post-shrink-to-fit).
         *
         * - BOTTOM-anchored: gap = safe area + user bottom offset (form sits above that margin)
         * - TOP-anchored:    gap = screenHeight - (safeAreaTop + user top offset) - formHeight
         * - CENTER:          gap = (screenHeight - formHeight) / 2 + (bottomMargin - topMargin) / 2,
         *                    accounting for the asymmetric-margin shift applied by
         *                    [calculateLayoutParams]
         * - FULLSCREEN:      gap = 0 (form covers the bottom edge)
         */
        internal fun calculateFormBottomGap(
            layout: FormLayout,
            formHeight: Int,
            screenHeight: Int,
            density: Float,
            safeAreaTop: Int,
            safeAreaBottom: Int
        ): Int {
            // Mirror the addSafeAreaInsetsToOffsets semantics used by [calculateLayoutParams]
            // so keyboard-overlap math stays consistent with the form's actual position.
            val effectiveSafeTop = if (layout.addSafeAreaInsetsToOffsets) safeAreaTop else 0
            val effectiveSafeBottom = if (layout.addSafeAreaInsetsToOffsets) safeAreaBottom else 0
            val topMargin = effectiveSafeTop + (layout.offsets.top * density).toInt()
            val bottomMargin = effectiveSafeBottom + (layout.offsets.bottom * density).toInt()

            val gap = when (layout.position) {
                FormPosition.BOTTOM,
                FormPosition.BOTTOM_LEFT,
                FormPosition.BOTTOM_RIGHT -> bottomMargin

                FormPosition.TOP,
                FormPosition.TOP_LEFT,
                FormPosition.TOP_RIGHT -> screenHeight - topMargin - formHeight

                FormPosition.CENTER -> (screenHeight - formHeight) / 2 + (bottomMargin - topMargin) / 2

                FormPosition.FULLSCREEN -> 0
            }
            // Forms taller than available space (or heavily asymmetric CENTER margins)
            // can push the form bottom edge off-screen, which would otherwise produce a
            // negative gap and over-shift the keyboard adjustment.
            return gap.coerceAtLeast(0)
        }
    }
}
