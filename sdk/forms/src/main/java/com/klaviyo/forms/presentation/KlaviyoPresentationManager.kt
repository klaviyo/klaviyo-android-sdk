package com.klaviyo.forms.presentation

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.core.utils.takeIf
import com.klaviyo.core.utils.takeIfNot
import com.klaviyo.forms.webview.WebViewClient

/**
 * Coordinates preloading klaviyo.js and presentation forms in an overlay activity
 */
internal class KlaviyoPresentationManager() : PresentationManager {
    private var overlayActivity by WeakReferenceDelegate<KlaviyoFormsOverlayActivity>(null)

    override var presentationState: PresentationState = PresentationState.Hidden
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
     *
     * TODO handle rotation better, including enum or typealias for orientation.
     */
    private fun onActivityEvent(event: ActivityEvent) = when (event) {
        is ActivityEvent.Created -> event.activity.takeIf<KlaviyoFormsOverlayActivity>()?.let { activity ->
            val formId = presentationState.takeIf<PresentationState.Presenting>()?.formId
            overlayActivity = activity
            Registry.get<WebViewClient>().attachWebView(activity)
            presentationState = PresentationState.Presented(formId)
        }

        is ActivityEvent.ConfigurationChanged -> presentationState.takeIfNot<PresentationState.Hidden>()?.let {
            val newOrientation = event.newConfig.orientation
            if (orientation != newOrientation) {
                Registry.log.debug("New screen orientation, closing form")
                dismiss()
            }
            orientation = newOrientation
        }

        else -> Unit
    }

    /**
     * Launch the overlay activity
     */
    override fun present(formId: String?) = presentationState.takeIf<PresentationState.Hidden>()?.let {
        presentationState = PresentationState.Presenting(formId)
        Registry.config.applicationContext.startActivity(KlaviyoFormsOverlayActivity.launchIntent)
    } ?: run {
        Registry.log.debug("Cannot present activity, currently in state: $presentationState")
    }

    /**
     * Detach the webview from the overlay activity and finish it
     */
    override fun dismiss() = overlayActivity?.let { activity ->
        Registry.log.debug("Dismissing form overlay activity")
        Registry.get<WebViewClient>().detachWebView(activity)
        activity.finish()
        presentationState = PresentationState.Hidden
    } ?: run {
        Registry.log.debug("No-op dismiss: overlay activity is not presented")
    }
}
