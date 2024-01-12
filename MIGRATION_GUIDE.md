# Migration Guide
This document provides guidance on how to migrate from the old version of the SDK to a newer version. 
It will be updated as new versions are released including deprecations or breaking changes.

## 2.0.0 Breaking Changes
*Type of `Event.value` has been correct to `Double`*
In version 1.x, `Event.value` was incorrectly typed as `String`. Klaviyo's API expects `value` to be numeric, and 
while the backend will implicitly convert a numeric string to a number, it is better to be explicit about the type.

## 1.4.0 Deprecations
*`EventType` is deprecated in favor of `EventMetric`.* `EventType` will be removed in the next major release.

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
