package com.klaviyo.analytics.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordsTest {
    @Test
    fun `Profile attribute keys`() {
        assertEquals("external_id", ProfileKey.EXTERNAL_ID.name)
        assertEquals("email", ProfileKey.EMAIL.name)
        assertEquals("phone_number", ProfileKey.PHONE_NUMBER.name)
        assertEquals("anonymous_id", ProfileKey.ANONYMOUS_ID.name)
        assertEquals("first_name", ProfileKey.FIRST_NAME.name)
        assertEquals("last_name", ProfileKey.LAST_NAME.name)
        assertEquals("organization", ProfileKey.ORGANIZATION.name)
        assertEquals("title", ProfileKey.TITLE.name)
        assertEquals("image", ProfileKey.IMAGE.name)
        assertEquals("address1", ProfileKey.ADDRESS1.name)
        assertEquals("address2", ProfileKey.ADDRESS2.name)
        assertEquals("city", ProfileKey.CITY.name)
        assertEquals("latitude", ProfileKey.LATITUDE.name)
        assertEquals("longitude", ProfileKey.LONGITUDE.name)
        assertEquals("region", ProfileKey.REGION.name)
        assertEquals("country", ProfileKey.COUNTRY.name)
        assertEquals("zip", ProfileKey.ZIP.name)
        assertEquals("timezone", ProfileKey.TIMEZONE.name)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, ProfileKey.CUSTOM(expectedCustomKey).name)

        // Test the equals operator works properly on custom keys
        val custom = ProfileKey.CUSTOM(expectedCustomKey)
        assert(custom == ProfileKey.CUSTOM(expectedCustomKey))
        assert(custom != ProfileKey.CUSTOM(expectedCustomKey + "1"))

        assertEquals("\$anonymous", ProfileKey.ANONYMOUS_ID.specialKey())
        assertEquals("\$id", ProfileKey.EXTERNAL_ID.specialKey())
        assertEquals("\$email", ProfileKey.EMAIL.specialKey())
        assertEquals("\$phone_number", ProfileKey.PHONE_NUMBER.specialKey())
        assertEquals("custom", ProfileKey.CUSTOM("custom").specialKey())

        assertEquals(ProfileKey.EMAIL, ProfileKey.CUSTOM("email"))
    }

    @Test
    fun `Event type keys`() {
        assertEquals("\$opened_push", MetricName.OPENED_PUSH.name)
        assertEquals("Opened App", MetricName.OPENED_APP.name)
        assertEquals("Viewed Product", MetricName.VIEWED_PRODUCT.name)
        assertEquals("Added to Cart", MetricName.ADDED_TO_CART.name)
        assertEquals("Started Checkout", MetricName.STARTED_CHECKOUT.name)
        assertEquals("custom", MetricName.CUSTOM("custom").name)

        assertEquals("\$opened_push", EventType.OPENED_PUSH.name)
        assertEquals("\$viewed_product", EventType.VIEWED_PRODUCT.name)
        assertEquals("\$searched_products", EventType.SEARCHED_PRODUCTS.name)
        assertEquals("\$started_checkout", EventType.STARTED_CHECKOUT.name)
        assertEquals("\$placed_order", EventType.PLACED_ORDER.name)
        assertEquals("\$ordered_product", EventType.ORDERED_PRODUCT.name)
        assertEquals("\$cancelled_order", EventType.CANCELLED_ORDER.name)
        assertEquals("\$refunded_order", EventType.REFUNDED_ORDER.name)
        assertEquals("\$paid_for_order", EventType.PAID_FOR_ORDER.name)
        assertEquals("\$subscribed_to_back_in_stock", EventType.SUBSCRIBED_TO_BACK_IN_STOCK.name)
        assertEquals("\$subscribed_to_coming_soon", EventType.SUBSCRIBED_TO_COMING_SOON.name)
        assertEquals("\$subscribed_to_list", EventType.SUBSCRIBED_TO_LIST.name)
        assertEquals("\$successful_payment", EventType.SUCCESSFUL_PAYMENT.name)
        assertEquals("\$failed_payment", EventType.FAILED_PAYMENT.name)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, MetricName.CUSTOM(expectedCustomKey).name)
        assertEquals(expectedCustomKey, EventType.CUSTOM(expectedCustomKey).name)

        // Test the equals operator works properly on custom keys
        var custom: MetricName = MetricName.CUSTOM(expectedCustomKey)
        assert(custom == MetricName.CUSTOM(expectedCustomKey))
        assert(custom != MetricName.CUSTOM(expectedCustomKey + "1"))

        // Test the equals operator works properly on custom keys
        custom = EventType.CUSTOM(expectedCustomKey)
        assert(custom == EventType.CUSTOM(expectedCustomKey))
        assert(custom != EventType.CUSTOM(expectedCustomKey + "1"))
    }

    @Test
    fun `Event keys`() {
        assertEquals("\$event_id", EventKey.EVENT_ID.name)
        assertEquals("\$value", EventKey.VALUE.name)
        assertEquals("push_token", EventKey.PUSH_TOKEN.name)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, EventKey.CUSTOM(expectedCustomKey).name)

        // Test the equals operator works properly on custom keys
        val custom = EventKey.CUSTOM(expectedCustomKey)
        assert(custom == EventKey.CUSTOM(expectedCustomKey))
        assert(custom != EventKey.CUSTOM(expectedCustomKey + "1"))
    }
}
