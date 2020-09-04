package com.klaviyo.coresdk.networking

sealed class KlaviyoEvent(val name: String) {
    object CHECKOUT_STARTED: KlaviyoEvent("checkout_started")
    object ORDER_PLACED: KlaviyoEvent("order_placed")
    class CUSTOM_EVENT(eventName: String): KlaviyoEvent(eventName)
}