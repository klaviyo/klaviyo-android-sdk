package com.klaviyo.forms.bridge

/**
 * Manages the collection of observers that inject data into the webview
 */
internal class KlaviyoObserverCollection : ObserverCollection {
    override val observers: List<Observer> = listOf(
        LifecycleObserver()
    )
}
