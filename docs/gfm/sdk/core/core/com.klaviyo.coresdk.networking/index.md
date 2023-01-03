//[core](../../index.md)/[com.klaviyo.coresdk.networking](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [KlaviyoCustomerProperties](-klaviyo-customer-properties/index.md) | [androidJvm]<br>class [KlaviyoCustomerProperties](-klaviyo-customer-properties/index.md) : [KlaviyoProperties](-klaviyo-properties/index.md)<br>Controls the data that can be input into a map of customer properties recognised by Klaviyo |
| [KlaviyoCustomerPropertyKeys](-klaviyo-customer-property-keys/index.md) | [androidJvm]<br>sealed class [KlaviyoCustomerPropertyKeys](-klaviyo-customer-property-keys/index.md) : [KlaviyoPropertyKeys](-klaviyo-property-keys/index.md)<br>All keys recognised by the Klaviyo APIs for identifying information within maps of customer properties |
| [KlaviyoEvent](-klaviyo-event/index.md) | [androidJvm]<br>sealed class [KlaviyoEvent](-klaviyo-event/index.md)<br>Events recognized by Klaviyo Custom events can be defined using the [CUSTOM_EVENT](-klaviyo-event/-c-u-s-t-o-m_-e-v-e-n-t/index.md) inner class |
| [KlaviyoEventProperties](-klaviyo-event-properties/index.md) | [androidJvm]<br>class [KlaviyoEventProperties](-klaviyo-event-properties/index.md) : [KlaviyoProperties](-klaviyo-properties/index.md)<br>Controls the data that can be input into a map of event properties recognised by Klaviyo |
| [KlaviyoEventPropertyKeys](-klaviyo-event-property-keys/index.md) | [androidJvm]<br>sealed class [KlaviyoEventPropertyKeys](-klaviyo-event-property-keys/index.md) : [KlaviyoPropertyKeys](-klaviyo-property-keys/index.md)<br>All keys recognised by the Klaviyo APIs for identifying information within maps of event properties |
| [KlaviyoProperties](-klaviyo-properties/index.md) | [androidJvm]<br>abstract class [KlaviyoProperties](-klaviyo-properties/index.md)<br>Abstract class that wraps around a map to control access to its contents. Provides helper functions to add properties to the map while controlling the keys available for entry |
| [KlaviyoPropertyKeys](-klaviyo-property-keys/index.md) | [androidJvm]<br>sealed class [KlaviyoPropertyKeys](-klaviyo-property-keys/index.md)<br>Base class used to provide polymorphic properties to the use of customer and event keys |
| [NetworkBatcher](-network-batcher/index.md) | [androidJvm]<br>object [NetworkBatcher](-network-batcher/index.md)<br>Class for handling a simple batcher for grouping up network requests |
