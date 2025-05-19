package com.klaviyo.forms.bridge

internal class KlaviyoObserverCollection : ObserverCollection {
    override val observers: List<Observer> = listOf(
        ProfileObserver()
    )
}
