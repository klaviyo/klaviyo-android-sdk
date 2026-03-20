package com.klaviyo.forms.presentation

import android.app.Activity
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
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
     * Handles device orientation change by observing all configuration changes
     * and re-attaching the webview if currently presented.
     *
     * TODO: Add floating window re-creation on orientation change. Currently the WindowManager
     *  LayoutParams become stale after the host Activity recreates, so the floating window
     *  position/size won't update after rotation.
     */
    private fun onConfigurationChanged(event: ActivityEvent.ConfigurationChanged) = safeCall {
        event.newConfig.orientation.takeIf { it != orientation }
            ?.also { newOrientation -> orientation = newOrientation }?.let {
                presentationState.takeIfNot<PresentationState, Hidden>()?.let {
                    orientation = event.newConfig.orientation
                    Registry.get<WebViewClient>().detachWebView()
                    presentationState = Presenting(it.formId)
                    Registry.log.debug("New screen orientation, detaching view")
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
            Registry.log.error("Cannot present floating form - WebView is null")
            presentationState = Hidden
            return
        }

        floatingFormWindow = FloatingFormWindow(activity).also { window ->
            window.show(activity, webView, layout)
        }

        presentationState = Presented(formId)
        Registry.log.debug("Presentation State: $presentationState")
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
            Registry.log.debug("No-op dismiss: nothing is currently presented")
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
     * Clear timers to stop any delayed side effects
     */
    private fun clearTimers() {
        // Run this cancel job now to stop any postponed form presentation
        cancelPostponedPresent?.runNow().also { cancelPostponedPresent = null }
        // Cancel the timeout for dismissing the overlay activity
        dismissOnTimeout?.cancel().also { dismissOnTimeout = null }
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
