//[core](../../../index.md)/[com.klaviyo.coresdk.networking](../index.md)/[KlaviyoProperties](index.md)

# KlaviyoProperties

[androidJvm]\
abstract class [KlaviyoProperties](index.md)

Abstract class that wraps around a map to control access to its contents. Provides helper functions to add properties to the map while controlling the keys available for entry

## Constructors

| | |
|---|---|
| [KlaviyoProperties](-klaviyo-properties.md) | [androidJvm]<br>fun [KlaviyoProperties](-klaviyo-properties.md)() |

## Functions

| Name | Summary |
|---|---|
| [addCustomProperty](add-custom-property.md) | [androidJvm]<br>abstract fun [addCustomProperty](add-custom-property.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), value: [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html)): [KlaviyoProperties](index.md)<br>Adds a custom property to the map. Custom properties can define any key name that isn't already reserved by Klaviyo |
| [addProperty](add-property.md) | [androidJvm]<br>open fun [addProperty](add-property.md)(propertyKey: [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md), value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KlaviyoProperties](index.md)<br>Adds a new key/value pair to the map. [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md) adds some control to what keys our property maps recognise |
| [set](set.md) | [androidJvm]<br>operator fun [set](set.md)(key: [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md), value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |

## Inheritors

| Name |
|---|
| [KlaviyoCustomerProperties](../-klaviyo-customer-properties/index.md) |
| [KlaviyoEventProperties](../-klaviyo-event-properties/index.md) |
