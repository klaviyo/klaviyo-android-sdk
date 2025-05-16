package com.klaviyo.forms.bridge

class InAppFormsObservers : Observers {
    override val observers: List<Observer> = listOf(
        KlaviyoProfileObserver()
    )
}
