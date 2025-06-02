package com.klaviyo.forms.presentation

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.utils.WeakReferenceDelegate
import com.klaviyo.core.utils.takeIf
import com.klaviyo.core.utils.takeIfNot
import com.klaviyo.forms.presentation.PresentationState.Hidden
import com.klaviyo.forms.presentation.PresentationState.Presented
import com.klaviyo.forms.presentation.PresentationState.Presenting
import com.klaviyo.forms.webview.WebViewClient

/**
 * Coordinates preloading klaviyo.js and presentation forms in an overlay activity
 */
internal class KlaviyoPresentationManager() : PresentationManager {
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
     *
     * TODO handle rotation better, including enum or typealias for orientation.
     */
    private fun onActivityEvent(event: ActivityEvent) = when (event) {
        is ActivityEvent.Created -> event.activity.takeIf<KlaviyoFormsOverlayActivity>()
            ?.let { activity ->
                presentationState.takeIf<Presenting>()?.let {
                    overlayActivity = activity
                    Registry.get<WebViewClient>().attachWebView(activity)
                    presentationState = Presented(it.formId)
                    Registry.log.debug(
                        "Finished presenting activity, now in state: $presentationState"
                    )
                }
            }

        is ActivityEvent.ConfigurationChanged -> event.newConfig.orientation.takeIf { it != orientation }
            ?.also { newOrientation -> orientation = newOrientation }
            ?.let {
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
     * Launch the overlay activity
     */
    override fun present(formId: String?) = presentationState.takeIf<Hidden>()?.let {
        presentationState = Presenting(formId)
        Registry.config.applicationContext.startActivity(KlaviyoFormsOverlayActivity.launchIntent)
    } ?: run {
        Registry.log.debug("Cannot present activity, currently in state: $presentationState")
    }

    /**
     * Detach the webview from the overlay activity and finish it
     */
    override fun dismiss() = overlayActivity?.let { activity ->
        Registry.log.debug("Dismissing form overlay activity")
        Registry.get<WebViewClient>().detachWebView()
        activity.finish()
        presentationState = Hidden
        overlayActivity = null
    } ?: run {
        Registry.log.debug("No-op dismiss: overlay activity is not presented")
    }
}
