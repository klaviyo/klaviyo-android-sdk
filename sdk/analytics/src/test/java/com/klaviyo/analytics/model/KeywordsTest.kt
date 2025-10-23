package com.klaviyo.analytics.model

import com.klaviyo.fixtures.BaseTest.Companion.EMAIL
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

        assertEquals(ProfileKey.EMAIL, ProfileKey.CUSTOM("email"))
    }

    @Test
    fun `A custom key is interchangeable with named key if name string matches`() {
        assertEquals(
            EMAIL,
            Profile(mapOf(ProfileKey.CUSTOM("email") to EMAIL)).email
        )
    }

    @Test
    fun `Event type keys`() {
        assertEquals("\$opened_push", EventMetric.OPENED_PUSH.name)
        assert(EventMetric.OPENED_PUSH.isInternal)
        assertEquals("Opened App", EventMetric.OPENED_APP.name)
        assertEquals("Viewed Product", EventMetric.VIEWED_PRODUCT.name)
        assertEquals("Added to Cart", EventMetric.ADDED_TO_CART.name)
        assertEquals("Started Checkout", EventMetric.STARTED_CHECKOUT.name)
        assertEquals("custom", EventMetric.CUSTOM("custom").name)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, EventMetric.CUSTOM(expectedCustomKey).name)

        // Test the equals operator works properly on custom keys
        val custom: EventMetric = EventMetric.CUSTOM(expectedCustomKey)
        assert(custom == EventMetric.CUSTOM(expectedCustomKey))
        assert(custom != EventMetric.CUSTOM(expectedCustomKey + "1"))
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
