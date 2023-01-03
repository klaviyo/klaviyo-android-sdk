//[core](../../../index.md)/[com.klaviyo.coresdk.networking](../index.md)/[KlaviyoCustomerProperties](index.md)

# KlaviyoCustomerProperties

[androidJvm]\
class [KlaviyoCustomerProperties](index.md) : [KlaviyoProperties](../-klaviyo-properties/index.md)

Controls the data that can be input into a map of customer properties recognised by Klaviyo

## Constructors

| | |
|---|---|
| [KlaviyoCustomerProperties](-klaviyo-customer-properties.md) | [androidJvm]<br>fun [KlaviyoCustomerProperties](-klaviyo-customer-properties.md)() |

## Functions

| Name | Summary |
|---|---|
| [addAppendProperty](add-append-property.md) | [androidJvm]<br>fun [addAppendProperty](add-append-property.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), value: [HashMap](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-hash-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [Any](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)&gt;): [KlaviyoCustomerProperties](index.md)<br>fun [addAppendProperty](add-append-property.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KlaviyoCustomerProperties](index.md) |
| [addCustomProperty](add-custom-property.md) | [androidJvm]<br>open override fun [addCustomProperty](add-custom-property.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), value: [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html)): [KlaviyoCustomerProperties](index.md)<br>Adds a custom property to the map. Custom properties can define any key name that isn't already reserved by Klaviyo |
| [addProperty](add-property.md) | [androidJvm]<br>open override fun [addProperty](add-property.md)(propertyKey: [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md), value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KlaviyoCustomerProperties](index.md)<br>Adds a new key/value pair to the map. [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md) adds some control to what keys our property maps recognise |
| [set](../-klaviyo-properties/set.md) | [androidJvm]<br>operator fun [set](../-klaviyo-properties/set.md)(key: [KlaviyoPropertyKeys](../-klaviyo-property-keys/index.md), value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [setEmail](set-email.md) | [androidJvm]<br>fun [setEmail](set-email.md)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KlaviyoCustomerProperties](index.md) |
| [setIdentifier](set-identifier.md) | [androidJvm]<br>fun [setIdentifier](set-identifier.md)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KlaviyoCustomerProperties](index.md) |
| [setPhoneNumber](set-phone-number.md) | [androidJvm]<br>fun [setPhoneNumber](set-phone-number.md)(value: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [KlaviyoCustomerProperties](index.md) |
