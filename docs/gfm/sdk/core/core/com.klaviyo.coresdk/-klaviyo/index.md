//[core](../../../index.md)/[com.klaviyo.coresdk](../index.md)/[Klaviyo](index.md)

# Klaviyo

[androidJvm]\
object [Klaviyo](index.md)

Public API for the core Klaviyo SDK. Receives configuration, customer data, and analytics requests to be processed and sent to the Klaviyo backend

## Functions

| Name | Summary |
|---|---|
| [configure](configure.md) | [androidJvm]<br>fun [configure](configure.md)(apiKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), applicationContext: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), networkTimeout: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = KlaviyoConfig.NETWORK_TIMEOUT_DEFAULT, networkFlushInterval: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = KlaviyoConfig.NETWORK_FLUSH_INTERVAL_DEFAULT, networkFlushDepth: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = KlaviyoConfig.NETWORK_FLUSH_DEPTH_DEFAULT, networkFlushCheckInterval: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = KlaviyoConfig.NETWORK_FLUSH_CHECK_INTERVAL, networkUseAnalyticsBatchQueue: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = KlaviyoConfig.NETWORK_USE_ANALYTICS_BATCH_QUEUE): [Klaviyo](index.md)<br>Configure Klaviyo SDK with your account's public API Key and application context. Optionally specify additional behavior customization |
| [createEvent](create-event.md) | [androidJvm]<br>fun [createEvent](create-event.md)(event: [KlaviyoEvent](../../com.klaviyo.coresdk.networking/-klaviyo-event/index.md), customerProperties: [KlaviyoCustomerProperties](../../com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md)? = null, properties: [KlaviyoEventProperties](../../com.klaviyo.coresdk.networking/-klaviyo-event-properties/index.md)? = null)<br>Queues a request to track a [KlaviyoEvent](../../com.klaviyo.coresdk.networking/-klaviyo-event/index.md) to the Klaviyo API The event will be associated with the profile specified by the [KlaviyoCustomerProperties](../../com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md) If customer properties are not set, this will fallback on the current profile identifiers |
| [createProfile](create-profile.md) | [androidJvm]<br>fun [createProfile](create-profile.md)(properties: [KlaviyoCustomerProperties](../../com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md))<br>Queues a request to identify profile properties to the Klaviyo API Identify requests track specific properties about a user without triggering an event |
| [reset](reset.md) | [androidJvm]<br>fun [reset](reset.md)(): [Klaviyo](index.md)<br>Clears all stored UserInfo identifiers (e.g. email or phone) |
| [setEmail](set-email.md) | [androidJvm]<br>fun [setEmail](set-email.md)(email: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Klaviyo](index.md)<br>Assigns an email address to the current UserInfo |
| [setPhone](set-phone.md) | [androidJvm]<br>fun [setPhone](set-phone.md)(phone: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Klaviyo](index.md)<br>Assigns a phone number to the current UserInfo |
