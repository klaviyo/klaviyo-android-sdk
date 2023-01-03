//[core](../../../index.md)/[com.klaviyo.coresdk](../index.md)/[Klaviyo](index.md)/[createEvent](create-event.md)

# createEvent

[androidJvm]\
fun [createEvent](create-event.md)(event: [KlaviyoEvent](../../com.klaviyo.coresdk.networking/-klaviyo-event/index.md), customerProperties: [KlaviyoCustomerProperties](../../com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md)? = null, properties: [KlaviyoEventProperties](../../com.klaviyo.coresdk.networking/-klaviyo-event-properties/index.md)? = null)

Queues a request to track a [KlaviyoEvent](../../com.klaviyo.coresdk.networking/-klaviyo-event/index.md) to the Klaviyo API The event will be associated with the profile specified by the [KlaviyoCustomerProperties](../../com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md) If customer properties are not set, this will fallback on the current profile identifiers

#### Parameters

androidJvm

| | |
|---|---|
| customerProperties | A map of customer property information. Defines the customer that triggered this event |
| properties | A map of event property information. Additional properties associated to the event that are not for identifying the customer |
