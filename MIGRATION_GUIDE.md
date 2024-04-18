# Migration Guide
This document provides guidance on how to migrate from the old version of the SDK to a newer version. 
It will be updated as new versions are released including deprecations or breaking changes.

## 2.1.0 Deprecations
#### Deprecated `ProfileKey` objects pertaining to identifiers
The following `ProfileKey` objects have been deprecated in favor of using the explicit 
setter/getter functions designated for identifiers. 
- `ProfileKey.EXTERNAL_ID`
- `ProfileKey.EMAIL`
- `ProfileKey.PHONE_NUMBER`

For convenience, we added optional arguments to the `Profile` constructor for each. The following code:

```kotlin
val profile = Profile(mapOf(
    ProfileKey.EXTERNAL_ID to "abc123",
    ProfileKey.EMAIL to "example@company.com",
    ProfileKey.PHONE_NUMBER to "+12223334444",
    ProfileKey.FIRST_NAME to "first_name",
    ProfileKey.LAST_NAME to "last_name",
))
```

can be converted to:

```kotlin
val profile = Profile(
    externalId = "abc123",
    email = "example@company.com",
    phoneNumber = "+12223334444", 
    properties = mapOf(
        ProfileKey.FIRST_NAME to "first_name",
        ProfileKey.LAST_NAME to "last_name",
    )
)
```

## 2.1.0 Deprecations
#### Deprecated `Klaviyo.lifecycleCallbacks`
In an effort to reduce setup code required to integrate the Klaviyo Android SDK, we have deprecated the public property 
`Klaviyo.lifecycleCallbacks` and will now register for lifecycle callbacks automatically upon `initialize`.
It is no longer required to have this line in your `Application.onCreate()` method:
```kotlin
registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
```
For version 2.1.x, `Klaviyo.lifecycleCallbacks` has been replaced with a no-op implementation to avoid duplicative
listeners, and will be removed altogether in the next major release.

## 2.0.0 Breaking Changes
#### Removed `EventType` in favor of `EventMetric`.
The reasoning is explained below, see [1.4.0 Deprecations](#140-deprecations) for details and code samples.
Additionally, for consistent naming conventions `Event.type` has been renamed to `Event.metric`,
including all argument labels in `Event` constructors. For example:

```kotlin
//Old code: Will no longer compile
val event = Event(type="Custom Event")

//New code: Corrected argument label
val event = Event(metric="Custom Event")
```

#### Corrected `Event.value` from `String` to `Double`
In version 1.x, `Event.value` was incorrectly typed as `String`. Klaviyo's API expects `value` to be numeric, and 
while the backend will implicitly convert a numeric string to a number, it is better to be explicit about the type.
```kotlin
// Old code: accepted strings (though still would be converted to a number on the server)
val event = Event("Test").setValue("1.0")
// Or
val event = Event("Test")
event.value = "1.0"

//New code: type has been corrected to Double
val event = Event("Test").setValue(1.0)
// Or
val event = Event("Test")
event.value = 1.0
```

## 1.4.0 Deprecations
#### Deprecated `EventType` in favor of `EventMetric`
It was recently discovered that the Android SDK was using legacy event names for some common events, 
like "Viewed Product" and some events that are associated with server actions, like "Ordered Product."
As a result, if your account used these standard events, they were being logged with names like "$viewed_product"
in contrast to website generated events which are logged as "Viewed Product."

In order to bring the Android SDK in line with Klaviyo's other integrations, we deprecated `EventType` and introduced 
`EventMetric` with corrected spellings. The old `EventType` values will still compile with a deprecation warning.

```kotlin
// Old code: Will log the legacy event names
import com.klaviyo.analytics.model.EventType

Klaviyo.createEvent(Event(EventType.VIEWED_PRODUCT))
Klaviyo.createEvent(Event(EventType.SEARCHED_PRODUCTS))
```

```kotlin
// New code: Will log the corrected event metric name
import com.klaviyo.analytics.model.EventMetric

Klaviyo.createEvent(Event(EventMetric.VIEWED_PRODUCT))
// If you still require old event names, you can use the CUSTOM metric e.g. 
Klaviyo.createEvent(Event(EventMetric.CUSTOM("\$viewed_product")))
Klaviyo.createEvent(Event(EventMetric.CUSTOM("\$searched_products")))
```
