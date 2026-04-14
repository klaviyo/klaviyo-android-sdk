package com.klaviyo.forms.presentation

import android.app.Activity
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.safeCall
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.core.utils.takeIf
import com.klaviyo.core.utils.takeIfNot
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.bridge.FormId
import com.klaviyo.forms.bridge.FormLayout
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.presentation.PresentationState.Hidden
import com.klaviyo.forms.presentation.PresentationState.Presented
import com.klaviyo.forms.presentation.PresentationState.Presenting
import com.klaviyo.forms.webview.WebViewClient

/**
 * Coordinates preloading klaviyo.js and presentation forms in an overlay activity
 * or a floating window (WindowManager approach) depending on layout configuration.
 */
internal class KlaviyoPresentationManager() : PresentationManager {
    /**
     * If we postpone presenting a form,
     * this token can be used to cancel that delayed job
     */
    private var cancelPostponedPresent: Clock.Cancellable? = null

    /**
     * If we invoke JS to close a form, this represents a timeout
     * to dismiss the overlay activity if we don't get a formDisappeared event from JS
     */
    private var dismissOnTimeout: Clock.Cancellable? = null

    /**
     * Observer waiting for the next Resumed activity after rotation to re-present a floating window.
     * Tracked at class level so it can be cleaned up if dismiss() is called while pending.
     */
    private var rotationObserver: ActivityObserver? = null

    /**
     * Delayed cleanup for multi-activity transitions. When the host activity stops,
     * we schedule cleanup after a grace period. If AllStopped fires within the grace
     * period (app backgrounding), it cancels this and handles re-presentation instead.
     * If the grace period expires without AllStopped, this is a multi-activity transition
     * and we permanently dismiss the floating window to avoid leaking the dead Activity.
     */
    private var hostActivityStoppedCleanup: Clock.Cancellable? = null

    private var overlayActivity by WeakReferenceDelegate<KlaviyoFormsOverlayActivity>(null)

    /**
     * The floating form window used for non-fullscreen layouts (WindowManager approach)
     */
    private var floatingFormWindow: FloatingFormWindow? = null

    /**
     * Weak reference to the host activity for floating window presentation
     */
    private var hostActivity by WeakReferenceDelegate<Activity>(null)

    override var presentationState: PresentationState = Hidden
        private set

    override var currentLayout: FormLayout? = null
        private set

    /**
     * For tracking device rotation
     */
    private var orientation: Int? = null

    init {
        Registry.lifecycleMonitor.onActivityEvent(::onActivityEvent)
    }

    private fun onActivityEvent(event: ActivityEvent) = when (event) {
        is ActivityEvent.Created -> onCreateActivity(event)
        is ActivityEvent.ConfigurationChanged -> onConfigurationChanged(event)
        is ActivityEvent.Stopped -> onActivityStopped(event)
        is ActivityEvent.AllStopped -> onAllStopped()
        else -> Unit
    }

    /**
     * Handles attaching the webview to the overlay activity once it is created.
     */
    private fun onCreateActivity(event: ActivityEvent.Created) = safeCall {
        event.activity.takeIf<KlaviyoFormsOverlayActivity>()?.let { activity ->
            presentationState.takeIf<Presenting>()?.let {
                overlayActivity = activity
                Registry.get<WebViewClient>().attachWebView(activity)
                presentationState = Presented(it.formId)
                Registry.log.debug("Presentation State: $presentationState")
            }
        }
    }

    /**
     * Handles the host activity stopping in a multi-activity app.
     *
     * When the host activity (the one showing the floating form) stops, we schedule
     * a delayed cleanup. If [onAllStopped] fires within the grace period (meaning the
     * app is backgrounding), it cancels this cleanup and handles re-presentation instead.
     * If the grace period expires without [onAllStopped], this is a multi-activity
     * transition and we permanently dismiss the floating window to avoid leaking the
     * dead Activity reference held by [FloatingFormWindow].
     */
    private fun onActivityStopped(event: ActivityEvent.Stopped) = safeCall {
        floatingFormWindow ?: return@safeCall
        if (event.activity !== hostActivity) return@safeCall

        hostActivityStoppedCleanup = Registry.clock.schedule(
            LifecycleMonitor.ACTIVITY_TRANSITION_GRACE_PERIOD
        ) {
            floatingFormWindow?.let { window ->
                Registry.get<WebViewClient>().detachWebView()
                window.dismiss()
                floatingFormWindow = null
                hostActivity = null
                currentLayout = null
                presentationState = Hidden
                Registry.log.debug(
                    "Host activity stopped in multi-activity transition, " +
                        "dismissed floating window"
                )
            }
        }
    }

    /**
     * Handles device orientation change by observing all configuration changes
     * and re-attaching the webview if currently presented.
     *
     * For Activity-based forms: detaches the webview and sets state to Presenting.
     * The overlay activity will re-attach via [onCreateActivity] after recreation.
     *
     * For floating windows: dismisses the window (invalidated by activity recreation)
     * and re-presents with the saved layout once the new activity is available.
     */
    private fun onConfigurationChanged(event: ActivityEvent.ConfigurationChanged) = safeCall {
        event.newConfig.orientation.takeIf { it != orientation }
            ?.also { newOrientation -> orientation = newOrientation }?.let {
                // Cancel any pending per-activity cleanup — rotation handles its own re-presentation.
                // Must be inside the orientation guard so non-orientation config changes (locale,
                // dark mode, font scale) don't cancel the timer without scheduling a replacement.
                hostActivityStoppedCleanup?.cancel()
                hostActivityStoppedCleanup = null

                presentationState.takeIfNot<PresentationState, Hidden>()?.let { state ->
                    orientation = event.newConfig.orientation
                    Registry.get<WebViewClient>().detachWebView()
                    presentationState = Presenting(state.formId)
                    Registry.log.debug("New screen orientation, detaching view")

                    // For floating windows, dismiss and re-present with new activity token.
                    // We must wait for the NEXT Resumed activity, not use currentActivity,
                    // because ConfigurationChanged fires before the old activity is destroyed.
                    // runWithCurrentOrNextActivity would shortcut with the stale activity.
                    floatingFormWindow?.let { window ->
                        window.dismiss()
                        floatingFormWindow = null

                        val layout = currentLayout ?: run {
                            presentationState = Hidden
                            Registry.log.warning("Rotation aborted: no layout to re-present")
                            return@safeCall
                        }
                        val floatingLayout = layout.takeUnless { it.isFullscreen } ?: run {
                            presentationState = Hidden
                            Registry.log.warning("Rotation aborted: layout is fullscreen")
                            return@safeCall
                        }

                        val observer: ActivityObserver = { activityEvent ->
                            activityEvent.takeIf<ActivityEvent.Resumed>()?.let { resumed ->
                                rotationObserver?.let {
                                    Registry.lifecycleMonitor.offActivityEvent(it)
                                }
                                rotationObserver = null
                                // Post to ensure window token is available — it's null
                                // during onResume but valid after the view hierarchy attaches
                                resumed.activity.window.decorView.post {
                                    // Guard: dismiss() may have run between post and execution
                                    if (presentationState is Hidden) return@post
                                    presentFloatingWindow(
                                        resumed.activity,
                                        state.formId,
                                        floatingLayout
                                    )
                                }
                            }
                        }
                        rotationObserver = observer
                        Registry.lifecycleMonitor.onActivityEvent(observer)
                    }
                }
            }
    }

    /**
     * Handles the app moving to the background (all activities stopped).
     *
     * Floating windows must be dismissed because their window token is tied to the host
     * activity, which may be destroyed while backgrounded. The form state and layout are
     * preserved so the window can be re-presented when the app returns to the foreground.
     *
     * Activity-based forms handle their own lifecycle and don't need intervention here.
     */
    private fun onAllStopped() = safeCall {
        // Cancel any pending per-activity cleanup — we handle it here with re-presentation
        hostActivityStoppedCleanup?.cancel()
        hostActivityStoppedCleanup = null

        floatingFormWindow?.let { window ->
            val state = presentationState.takeIfNot<PresentationState, Hidden>() ?: return@safeCall

            Registry.get<WebViewClient>().detachWebView()
            window.dismiss()
            floatingFormWindow = null
            presentationState = Presenting(state.formId)
            Registry.log.debug("App backgrounded, dismissed floating window for re-presentation")

            val layout = currentLayout ?: run {
                presentationState = Hidden
                Registry.log.warning("Background re-presentation aborted: no layout")
                return@safeCall
            }
            val floatingLayout = layout.takeUnless { it.isFullscreen } ?: run {
                presentationState = Hidden
                Registry.log.warning("Background re-presentation aborted: layout is fullscreen")
                return@safeCall
            }

            // Re-present when the app returns to the foreground with a valid activity
            val timeout = Registry.get<InAppFormsConfig>().getSessionTimeoutDuration().inWholeMilliseconds
            cancelPostponedPresent = Registry.lifecycleMonitor.runWithCurrentOrNextActivity(
                timeout = timeout
            ) { activity ->
                // Cancel the fallback since we're successfully re-presenting
                dismissOnTimeout?.cancel()
                dismissOnTimeout = null
                // Post to ensure window token is available after activity resumes
                activity.window.decorView.post {
                    // Guard: dismiss() may have run between post and execution
                    if (presentationState is Hidden) return@post
                    presentFloatingWindow(activity, state.formId, floatingLayout)
                }
            }

            // Cancel any existing dismiss timer before scheduling a new one to prevent
            // orphaned timers firing on a dead context (e.g. if closeFormAndDismiss set
            // a 400ms timer and the app backgrounds within that window)
            dismissOnTimeout?.cancel()

            // Fallback: if the timeout fires without re-presentation, reset state to Hidden
            // so future present() calls aren't blocked by a stale Presenting state
            dismissOnTimeout = Registry.clock.schedule(timeout) {
                presentationState.takeIf<Presenting>()?.let {
                    presentationState = Hidden
                    currentLayout = null
                    Registry.log.debug("Background re-presentation timed out, resetting to Hidden")
                }
            }
        }
    }

    /**
     * Present the form now if the app is foregrounded,
     * or else wait till next foregrounded unless session ends.
     *
     * For non-fullscreen layouts, uses FloatingFormWindow (WindowManager approach).
     * For fullscreen or null layout, uses the Activity-based approach.
     */
    override fun present(formId: FormId?, layout: FormLayout?) {
        clearTimers()
        currentLayout = layout

        // Capture non-null layout for floating window (non-fullscreen), null for Activity approach
        val floatingLayout: FormLayout? = layout?.takeUnless { it.isFullscreen }

        cancelPostponedPresent = Registry.lifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = Registry.get<InAppFormsConfig>().getSessionTimeoutDuration().inWholeMilliseconds
        ) { activity ->
            presentationState.takeIf<Hidden>()?.let {
                if (floatingLayout != null) {
                    presentFloatingWindow(activity, formId, floatingLayout)
                } else {
                    presentActivity(activity, formId)
                }
            } ?: run {
                Registry.log.debug("Cannot present form. Current state: $presentationState")
            }
        }
    }

    /**
     * Present the form using the Activity-based approach (fullscreen)
     */
    private fun presentActivity(activity: Activity, formId: FormId?) {
        presentationState = Presenting(formId)
        Registry.log.debug("Presentation State: $presentationState (Activity approach)")
        activity.startActivity(KlaviyoFormsOverlayActivity.launchIntent)
    }

    /**
     * Present the form using FloatingFormWindow (WindowManager approach)
     */
    private fun presentFloatingWindow(activity: Activity, formId: FormId?, layout: FormLayout) {
        presentationState = Presenting(formId)
        Registry.log.debug("Presentation State: $presentationState (WindowManager approach)")

        hostActivity = activity

        val webViewClient = Registry.get<WebViewClient>()
        val webView = webViewClient.getWebView()

        if (webView == null) {
            Registry.log.warning("Cannot present floating form - WebView is null")
            presentationState = Hidden
            return
        }

        floatingFormWindow = FloatingFormWindow(activity).also { window ->
            window.show(
                hostActivity = activity,
                webView = webView,
                layout = layout,
                onPresented = {
                    presentationState = Presented(formId)
                    Registry.log.debug("Presentation State: $presentationState")
                },
                onError = {
                    floatingFormWindow = null
                    hostActivity = null
                    currentLayout = null
                    presentationState = Hidden
                    Registry.log.debug("Presentation State: $presentationState (addView failed)")
                }
            )
        }
    }

    /**
     * Detach the webview from the overlay activity or floating window and dismiss it
     */
    override fun dismiss() {
        clearTimers()

        // Try to dismiss floating window first
        floatingFormWindow?.let { window ->
            Registry.get<WebViewClient>().detachWebView()
            window.dismiss()
            floatingFormWindow = null
            hostActivity = null
            currentLayout = null
            presentationState = Hidden
            Registry.log.debug(
                "Presentation State: $presentationState (FloatingFormWindow dismissed)"
            )
            return
        }

        // Fall back to Activity-based dismissal
        overlayActivity?.let { activity ->
            Registry.get<WebViewClient>().detachWebView()
            activity.finish()
            presentationState = Hidden
            overlayActivity = null
            currentLayout = null
            Registry.log.debug("Presentation State: $presentationState (Activity dismissed)")
        } ?: run {
            // Catch-all: reset state to Hidden if neither branch handled dismissal.
            // This covers mid-rotation dismiss where floatingFormWindow was already
            // nulled by onConfigurationChanged but state is still Presenting.
            if (presentationState !is Hidden) {
                presentationState = Hidden
                currentLayout = null
                Registry.log.debug(
                    "Presentation State: $presentationState (reset from stale state)"
                )
            } else {
                Registry.log.debug("No-op dismiss: nothing is currently presented")
            }
        }
    }

    /**
     * Close any open forms and dismiss the overlay activity
     */
    override fun closeFormAndDismiss() = presentationState.takeIf<Presented>()?.let {
        Registry.get<JsBridge>().closeForm(it.formId)
        dismissOnTimeout = Registry.clock.schedule(CLOSE_TIMEOUT, ::dismiss)
    } ?: dismiss().also {
        Registry.log.debug("Dismissing without closing form. Current state: $presentationState")
    }

    /**
     * Clear timers and observers to stop any delayed side effects
     */
    private fun clearTimers() {
        // Run this cancel job now to stop any postponed form presentation
        cancelPostponedPresent?.runNow().also { cancelPostponedPresent = null }
        // Cancel the timeout for dismissing the overlay activity
        dismissOnTimeout?.cancel().also { dismissOnTimeout = null }
        // Unregister any pending rotation observer to prevent re-presentation after dismiss
        rotationObserver?.let { Registry.lifecycleMonitor.offActivityEvent(it) }
        rotationObserver = null
        // Cancel any pending multi-activity transition cleanup
        hostActivityStoppedCleanup?.cancel()
        hostActivityStoppedCleanup = null
    }

    private companion object {
        /**
         * Grace period to close a form with animation, before we just dismiss
         *  the overlay activity without waiting for formDisappeared event
         *  ~350ms for the animation, and a little padding.
         */
        private const val CLOSE_TIMEOUT = 400L
    }
}
