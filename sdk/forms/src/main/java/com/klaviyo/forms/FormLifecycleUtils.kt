package com.klaviyo.forms

import com.klaviyo.core.Registry

/**
 * Invoke the registered form lifecycle callback on the UI thread, if one is registered.
 *
 * Shared utility used by both [com.klaviyo.forms.bridge.KlaviyoNativeBridge]
 * and [com.klaviyo.forms.presentation.KlaviyoPresentationManager].
 */
internal fun invokeFormLifecycleCallback(event: FormLifecycleEvent) {
    Registry.getOrNull<FormLifecycleCallback>()?.let { callback ->
        Registry.threadHelper.runOnUiThread {
            try {
                callback.onFormLifecycleEvent(event)
            } catch (e: Exception) {
                Registry.log.error("Form lifecycle callback threw an exception", e)
            }
        }
    }
}
