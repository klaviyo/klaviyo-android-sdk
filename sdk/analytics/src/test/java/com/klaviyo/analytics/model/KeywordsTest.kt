package com.klaviyo.analytics.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordsTest {
    private fun assertName(key: Keyword) =
        assertEquals("\$${key::class.simpleName?.lowercase()}", key.name)

    @Test
    fun `Profile attribute keys`() {
        assertName(ProfileKey.EXTERNAL_ID)
        assertName(ProfileKey.EMAIL)
        assertName(ProfileKey.PHONE_NUMBER)
        assertName(ProfileKey.ANONYMOUS)
        assertName(ProfileKey.FIRST_NAME)
        assertName(ProfileKey.LAST_NAME)
        assertName(ProfileKey.TITLE)
        assertName(ProfileKey.ORGANIZATION)
        assertName(ProfileKey.CITY)
        assertName(ProfileKey.REGION)
        assertName(ProfileKey.COUNTRY)
        assertName(ProfileKey.ZIP)
        assertName(ProfileKey.IMAGE)
        assertName(ProfileKey.CONSENT)
        assertName(ProfileKey.APPEND)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, ProfileKey.CUSTOM(expectedCustomKey).name)

        val custom = ProfileKey.CUSTOM(expectedCustomKey)
        assert(custom == ProfileKey.CUSTOM(expectedCustomKey))
        assert(custom != ProfileKey.CUSTOM(expectedCustomKey + "1"))
    }

    @Test
    fun `Event type keys`() {
        assertName(EventType.OPENED_PUSH)
        assertName(EventType.VIEWED_PRODUCT)
        assertName(EventType.SEARCHED_PRODUCTS)
        assertName(EventType.STARTED_CHECKOUT)
        assertName(EventType.PLACED_ORDER)
        assertName(EventType.ORDERED_PRODUCT)
        assertName(EventType.CANCELLED_ORDER)
        assertName(EventType.REFUNDED_ORDER)
        assertName(EventType.PAID_FOR_ORDER)
        assertName(EventType.SUBSCRIBED_TO_BACK_IN_STOCK)
        assertName(EventType.SUBSCRIBED_TO_COMING_SOON)
        assertName(EventType.SUBSCRIBED_TO_LIST)
        assertName(EventType.SUCCESSFUL_PAYMENT)
        assertName(EventType.FAILED_PAYMENT)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, EventType.CUSTOM(expectedCustomKey).name)

        val custom = EventType.CUSTOM(expectedCustomKey)
        assert(custom == EventType.CUSTOM(expectedCustomKey))
        assert(custom != EventType.CUSTOM(expectedCustomKey + "1"))
    }

    @Test
    fun `Event keys`() {
        assertName(EventKey.EVENT_ID)
        assertName(EventKey.VALUE)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, EventKey.CUSTOM(expectedCustomKey).name)

        val custom = EventKey.CUSTOM(expectedCustomKey)
        assert(custom == EventKey.CUSTOM(expectedCustomKey))
        assert(custom != EventKey.CUSTOM(expectedCustomKey + "1"))
    }
}
