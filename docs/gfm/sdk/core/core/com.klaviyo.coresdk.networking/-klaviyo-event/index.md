//[core](../../../index.md)/[com.klaviyo.coresdk.networking](../index.md)/[KlaviyoEvent](index.md)

# KlaviyoEvent

[androidJvm]\
sealed class [KlaviyoEvent](index.md)

Events recognized by Klaviyo Custom events can be defined using the [CUSTOM_EVENT](-c-u-s-t-o-m_-e-v-e-n-t/index.md) inner class

## Types

| Name | Summary |
|---|---|
| [ACTIVATED_SUBSCRIPTION](-a-c-t-i-v-a-t-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) | [androidJvm]<br>object [ACTIVATED_SUBSCRIPTION](-a-c-t-i-v-a-t-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) : [KlaviyoEvent](index.md) |
| [CANCELLED_ORDER](-c-a-n-c-e-l-l-e-d_-o-r-d-e-r/index.md) | [androidJvm]<br>object [CANCELLED_ORDER](-c-a-n-c-e-l-l-e-d_-o-r-d-e-r/index.md) : [KlaviyoEvent](index.md) |
| [CANCELLED_SUBSCRIPTION](-c-a-n-c-e-l-l-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) | [androidJvm]<br>object [CANCELLED_SUBSCRIPTION](-c-a-n-c-e-l-l-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) : [KlaviyoEvent](index.md) |
| [CLOSED_SUBSCRIPTION](-c-l-o-s-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) | [androidJvm]<br>object [CLOSED_SUBSCRIPTION](-c-l-o-s-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) : [KlaviyoEvent](index.md) |
| [COMPLETED_ORDER](-c-o-m-p-l-e-t-e-d_-o-r-d-e-r/index.md) | [androidJvm]<br>object [COMPLETED_ORDER](-c-o-m-p-l-e-t-e-d_-o-r-d-e-r/index.md) : [KlaviyoEvent](index.md) |
| [CREATED_SUBSCRIPTION](-c-r-e-a-t-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) | [androidJvm]<br>object [CREATED_SUBSCRIPTION](-c-r-e-a-t-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) : [KlaviyoEvent](index.md) |
| [CUSTOM_EVENT](-c-u-s-t-o-m_-e-v-e-n-t/index.md) | [androidJvm]<br>class [CUSTOM_EVENT](-c-u-s-t-o-m_-e-v-e-n-t/index.md)(eventName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [KlaviyoEvent](index.md) |
| [EXPIRED_SUBSCRIPTION](-e-x-p-i-r-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) | [androidJvm]<br>object [EXPIRED_SUBSCRIPTION](-e-x-p-i-r-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) : [KlaviyoEvent](index.md) |
| [FAILED_PAYMENT](-f-a-i-l-e-d_-p-a-y-m-e-n-t/index.md) | [androidJvm]<br>object [FAILED_PAYMENT](-f-a-i-l-e-d_-p-a-y-m-e-n-t/index.md) : [KlaviyoEvent](index.md) |
| [FULFILLED_ORDER](-f-u-l-f-i-l-l-e-d_-o-r-d-e-r/index.md) | [androidJvm]<br>object [FULFILLED_ORDER](-f-u-l-f-i-l-l-e-d_-o-r-d-e-r/index.md) : [KlaviyoEvent](index.md) |
| [FULFILLED_PRODUCT](-f-u-l-f-i-l-l-e-d_-p-r-o-d-u-c-t/index.md) | [androidJvm]<br>object [FULFILLED_PRODUCT](-f-u-l-f-i-l-l-e-d_-p-r-o-d-u-c-t/index.md) : [KlaviyoEvent](index.md) |
| [FULLFILLED_SHIPMENT](-f-u-l-l-f-i-l-l-e-d_-s-h-i-p-m-e-n-t/index.md) | [androidJvm]<br>object [FULLFILLED_SHIPMENT](-f-u-l-l-f-i-l-l-e-d_-s-h-i-p-m-e-n-t/index.md) : [KlaviyoEvent](index.md) |
| [ISSUED_INVOICE](-i-s-s-u-e-d_-i-n-v-o-i-c-e/index.md) | [androidJvm]<br>object [ISSUED_INVOICE](-i-s-s-u-e-d_-i-n-v-o-i-c-e/index.md) : [KlaviyoEvent](index.md) |
| [OPENED_PUSH](-o-p-e-n-e-d_-p-u-s-h/index.md) | [androidJvm]<br>object [OPENED_PUSH](-o-p-e-n-e-d_-p-u-s-h/index.md) : [KlaviyoEvent](index.md) |
| [ORDERED_PRODUCT](-o-r-d-e-r-e-d_-p-r-o-d-u-c-t/index.md) | [androidJvm]<br>object [ORDERED_PRODUCT](-o-r-d-e-r-e-d_-p-r-o-d-u-c-t/index.md) : [KlaviyoEvent](index.md) |
| [PAID_FOR_ORDER](-p-a-i-d_-f-o-r_-o-r-d-e-r/index.md) | [androidJvm]<br>object [PAID_FOR_ORDER](-p-a-i-d_-f-o-r_-o-r-d-e-r/index.md) : [KlaviyoEvent](index.md) |
| [PLACED_ORDER](-p-l-a-c-e-d_-o-r-d-e-r/index.md) | [androidJvm]<br>object [PLACED_ORDER](-p-l-a-c-e-d_-o-r-d-e-r/index.md) : [KlaviyoEvent](index.md) |
| [REFUNDED_ORDER](-r-e-f-u-n-d-e-d_-o-r-d-e-r/index.md) | [androidJvm]<br>object [REFUNDED_ORDER](-r-e-f-u-n-d-e-d_-o-r-d-e-r/index.md) : [KlaviyoEvent](index.md) |
| [REFUNDED_PAYMENT](-r-e-f-u-n-d-e-d_-p-a-y-m-e-n-t/index.md) | [androidJvm]<br>object [REFUNDED_PAYMENT](-r-e-f-u-n-d-e-d_-p-a-y-m-e-n-t/index.md) : [KlaviyoEvent](index.md) |
| [SEARCHED_PRODUCTS](-s-e-a-r-c-h-e-d_-p-r-o-d-u-c-t-s/index.md) | [androidJvm]<br>object [SEARCHED_PRODUCTS](-s-e-a-r-c-h-e-d_-p-r-o-d-u-c-t-s/index.md) : [KlaviyoEvent](index.md) |
| [SHIPPED_ORDER](-s-h-i-p-p-e-d_-o-r-d-e-r/index.md) | [androidJvm]<br>object [SHIPPED_ORDER](-s-h-i-p-p-e-d_-o-r-d-e-r/index.md) : [KlaviyoEvent](index.md) |
| [STARTED_CHECKOUT](-s-t-a-r-t-e-d_-c-h-e-c-k-o-u-t/index.md) | [androidJvm]<br>object [STARTED_CHECKOUT](-s-t-a-r-t-e-d_-c-h-e-c-k-o-u-t/index.md) : [KlaviyoEvent](index.md) |
| [SUBSCRIBED_TO_BACK_IN_STOCK](-s-u-b-s-c-r-i-b-e-d_-t-o_-b-a-c-k_-i-n_-s-t-o-c-k/index.md) | [androidJvm]<br>object [SUBSCRIBED_TO_BACK_IN_STOCK](-s-u-b-s-c-r-i-b-e-d_-t-o_-b-a-c-k_-i-n_-s-t-o-c-k/index.md) : [KlaviyoEvent](index.md) |
| [SUBSCRIBED_TO_COMING_SOON](-s-u-b-s-c-r-i-b-e-d_-t-o_-c-o-m-i-n-g_-s-o-o-n/index.md) | [androidJvm]<br>object [SUBSCRIBED_TO_COMING_SOON](-s-u-b-s-c-r-i-b-e-d_-t-o_-c-o-m-i-n-g_-s-o-o-n/index.md) : [KlaviyoEvent](index.md) |
| [SUBSCRIBED_TO_LIST](-s-u-b-s-c-r-i-b-e-d_-t-o_-l-i-s-t/index.md) | [androidJvm]<br>object [SUBSCRIBED_TO_LIST](-s-u-b-s-c-r-i-b-e-d_-t-o_-l-i-s-t/index.md) : [KlaviyoEvent](index.md) |
| [SUCCESSFUL_PAYMENT](-s-u-c-c-e-s-s-f-u-l_-p-a-y-m-e-n-t/index.md) | [androidJvm]<br>object [SUCCESSFUL_PAYMENT](-s-u-c-c-e-s-s-f-u-l_-p-a-y-m-e-n-t/index.md) : [KlaviyoEvent](index.md) |
| [VIEWED_PRODUCT](-v-i-e-w-e-d_-p-r-o-d-u-c-t/index.md) | [androidJvm]<br>object [VIEWED_PRODUCT](-v-i-e-w-e-d_-p-r-o-d-u-c-t/index.md) : [KlaviyoEvent](index.md) |

## Properties

| Name | Summary |
|---|---|
| [name](name.md) | [androidJvm]<br>val [name](name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>String value of the event which is recognized by Klaviyo as a registered event |

## Inheritors

| Name |
|---|
| [VIEWED_PRODUCT](-v-i-e-w-e-d_-p-r-o-d-u-c-t/index.md) |
| [SEARCHED_PRODUCTS](-s-e-a-r-c-h-e-d_-p-r-o-d-u-c-t-s/index.md) |
| [STARTED_CHECKOUT](-s-t-a-r-t-e-d_-c-h-e-c-k-o-u-t/index.md) |
| [PLACED_ORDER](-p-l-a-c-e-d_-o-r-d-e-r/index.md) |
| [ORDERED_PRODUCT](-o-r-d-e-r-e-d_-p-r-o-d-u-c-t/index.md) |
| [CANCELLED_ORDER](-c-a-n-c-e-l-l-e-d_-o-r-d-e-r/index.md) |
| [REFUNDED_ORDER](-r-e-f-u-n-d-e-d_-o-r-d-e-r/index.md) |
| [PAID_FOR_ORDER](-p-a-i-d_-f-o-r_-o-r-d-e-r/index.md) |
| [FULFILLED_ORDER](-f-u-l-f-i-l-l-e-d_-o-r-d-e-r/index.md) |
| [FULLFILLED_SHIPMENT](-f-u-l-l-f-i-l-l-e-d_-s-h-i-p-m-e-n-t/index.md) |
| [FULFILLED_PRODUCT](-f-u-l-f-i-l-l-e-d_-p-r-o-d-u-c-t/index.md) |
| [COMPLETED_ORDER](-c-o-m-p-l-e-t-e-d_-o-r-d-e-r/index.md) |
| [SHIPPED_ORDER](-s-h-i-p-p-e-d_-o-r-d-e-r/index.md) |
| [SUBSCRIBED_TO_BACK_IN_STOCK](-s-u-b-s-c-r-i-b-e-d_-t-o_-b-a-c-k_-i-n_-s-t-o-c-k/index.md) |
| [SUBSCRIBED_TO_COMING_SOON](-s-u-b-s-c-r-i-b-e-d_-t-o_-c-o-m-i-n-g_-s-o-o-n/index.md) |
| [SUBSCRIBED_TO_LIST](-s-u-b-s-c-r-i-b-e-d_-t-o_-l-i-s-t/index.md) |
| [SUCCESSFUL_PAYMENT](-s-u-c-c-e-s-s-f-u-l_-p-a-y-m-e-n-t/index.md) |
| [FAILED_PAYMENT](-f-a-i-l-e-d_-p-a-y-m-e-n-t/index.md) |
| [REFUNDED_PAYMENT](-r-e-f-u-n-d-e-d_-p-a-y-m-e-n-t/index.md) |
| [ISSUED_INVOICE](-i-s-s-u-e-d_-i-n-v-o-i-c-e/index.md) |
| [CREATED_SUBSCRIPTION](-c-r-e-a-t-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) |
| [ACTIVATED_SUBSCRIPTION](-a-c-t-i-v-a-t-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) |
| [CANCELLED_SUBSCRIPTION](-c-a-n-c-e-l-l-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) |
| [EXPIRED_SUBSCRIPTION](-e-x-p-i-r-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) |
| [CLOSED_SUBSCRIPTION](-c-l-o-s-e-d_-s-u-b-s-c-r-i-p-t-i-o-n/index.md) |
| [OPENED_PUSH](-o-p-e-n-e-d_-p-u-s-h/index.md) |
| [CUSTOM_EVENT](-c-u-s-t-o-m_-e-v-e-n-t/index.md) |
