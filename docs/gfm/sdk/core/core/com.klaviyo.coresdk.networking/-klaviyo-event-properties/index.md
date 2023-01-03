//[core](../../../index.md)/[com.klaviyo.coresdk.networking](../index.md)/[KlaviyoEventProperties](index.md)

# KlaviyoEventProperties

[androidJvm]\
class [KlaviyoEventProperties](index.md) : [KlaviyoProperties](../-klaviyo-properties/index.md)

Controls the data that can be input into a map of event properties recognised by Klaviyo

## Constructors

| | |
|---|---|
| [KlaviyoEventProperties](-klaviyo-event-properties.md) | [androidJvm]<br>fun [KlaviyoEventProperties](-klaviyo-event-properties.md)() |

## Functions

| Name | Summary |
|---|---|
| [addCustomProperty](add-custom-property.md) | [androidJvm]<br>open override fun [addCustomProperty](add-custom-property.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), value: [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html)): [KlaviyoEventProperties](index.md)<br>Adds a custom property to the map. Custom properties can define any key name that isn't already reserved by Klaviyo |
| [addProperty](add-property.md) | [androidJvm]<br>open override fun [addProperty](add-property.md)(propertyKey: [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md), value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KlaviyoEventProperties](index.md)<br>Adds a new key/value pair to the map. [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md) adds some control to what keys our property maps recognise |
| [addValue](add-value.md) | [androidJvm]<br>fun [addValue](add-value.md)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KlaviyoEventProperties](index.md) |
| [set](../-klaviyo-properties/set.md) | [androidJvm]<br>operator fun [set](../-klaviyo-properties/set.md)(key: [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md), value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
