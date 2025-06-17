package com.klaviyo.forms.presentation

import android.app.Activity
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.core.utils.takeIf
import com.klaviyo.core.utils.takeIfNot
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.bridge.FormId
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.presentation.PresentationState.Hidden
import com.klaviyo.forms.presentation.PresentationState.Presented
import com.klaviyo.forms.presentation.PresentationState.Presenting
import com.klaviyo.forms.webview.WebViewClient

/**
 * Coordinates preloading klaviyo.js and presentation forms in an overlay activity
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

    override var presentationState: PresentationState = Hidden
        private set

    /**
     * For tracking device rotation
     */
    private var orientation: Int? = null

    init {
        Registry.lifecycleMonitor.onActivityEvent(::onActivityEvent)
    }

    /**
     * This closes the form on rotation, which we can detect with the local field
     * We wait for a change, see if it's different from the current, and close an open webview
     */
    private fun onActivityEvent(event: ActivityEvent) = when (event) {
        is ActivityEvent.Created -> event.activity.takeIf<KlaviyoFormsOverlayActivity>()?.let { activity ->
            presentationState.takeIf<Presenting>()?.let {
                overlayActivity = activity
                Registry.get<WebViewClient>().attachWebView(activity)
                presentationState = Presented(it.formId)
                Registry.log.debug("Presentation State: $presentationState")
            }
        }

        is ActivityEvent.ConfigurationChanged -> event.newConfig.orientation.takeIf { it != orientation }
            ?.also { newOrientation -> orientation = newOrientation }?.let {
                presentationState.takeIfNot<PresentationState, Hidden>()?.let {
                    orientation = event.newConfig.orientation
                    Registry.get<WebViewClient>().detachWebView()
                    presentationState = Presenting(it.formId)
                    Registry.log.debug("New screen orientation, detaching view")
                }
            }

        else -> Unit
    }

    /**
     * Present the form now if the app is foregrounded,
     * or else wait till next foregrounded unless session ends
     */
    override fun present(formId: FormId?) {
        clearTimers()
        cancelPostponedPresent = Registry.lifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = Registry.get<InAppFormsConfig>().getSessionTimeoutDurationInMillis()
        ) {
            presentationState.takeIf<Hidden>()?.let {
                presentationState = Presenting(formId)
                Registry.log.debug("Presentation State: $presentationState")
                Registry.config.applicationContext.startActivity(
                    KlaviyoFormsOverlayActivity.launchIntent
                )
            } ?: run {
                Registry.log.debug("Cannot present activity. Current state: $presentationState")
            }
        }
    }

    /**
     * Detach the webview from the overlay activity and finish it
     */
    override fun dismiss() = overlayActivity?.let { activity ->
        clearTimers()
        Registry.get<WebViewClient>().detachWebView()
        activity.finish()
        presentationState = Hidden
        overlayActivity = null
        Registry.log.debug("Presentation State: $presentationState")
    } ?: clearTimers().also {
        Registry.log.debug("No-op dismiss: overlay activity is not presented")
    }

    /**
     * Close any open forms and dismiss the overlay activity
     */
    override fun closeFormAndDismiss() = presentationState.takeIf<Presented>()?.let {
        Registry.get<JsBridge>().closeForm()
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

/**
 * Helper function to run a task immediately if there is a current activity,
 * or wait for the next resumed activity if resumed within the optional timeout.
 * Returns a token that can be used to cancel the pending task if needed.
 */
internal fun LifecycleMonitor.runWithCurrentOrNextActivity(
    timeout: Long? = null,
    job: (activity: Activity) -> Unit
): Clock.Cancellable? {
    currentActivity?.let { activity ->
        job(activity)
        return null
    }

    var observer: ActivityObserver? = null
    val token: Clock.Cancellable? = timeout?.let { delay ->
        Registry.log.wtf("Cancel task scheduled for $delay ms")
        Registry.clock.schedule(delay) {
            Registry.log.verbose("Removing postponed observer after timeout")
            observer?.let { offActivityEvent(it) }
        }
    }
    observer = { event ->
        event.takeIf<ActivityEvent.Resumed>()?.let { event ->
            Registry.log.verbose("Invoking postponed observer on resume")
            job(event.activity)
            observer?.let { offActivityEvent(it) }
            token?.cancel()
        }
    }
    onActivityEvent(observer)

    return token
}
