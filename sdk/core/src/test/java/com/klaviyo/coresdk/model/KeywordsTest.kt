package com.klaviyo.coresdk.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordsTest {
    private fun assertName(key: KlaviyoKeyword) =
        assertEquals("\$${key::class.simpleName?.lowercase()}", key.name)

    @Test
    fun `Profile attribute keys`() {
        assertName(KlaviyoProfileAttributeKey.EXTERNAL_ID)
        assertName(KlaviyoProfileAttributeKey.EMAIL)
        assertName(KlaviyoProfileAttributeKey.PHONE_NUMBER)
        assertName(KlaviyoProfileAttributeKey.ANONYMOUS)
        assertName(KlaviyoProfileAttributeKey.FIRST_NAME)
        assertName(KlaviyoProfileAttributeKey.LAST_NAME)
        assertName(KlaviyoProfileAttributeKey.TITLE)
        assertName(KlaviyoProfileAttributeKey.ORGANIZATION)
        assertName(KlaviyoProfileAttributeKey.CITY)
        assertName(KlaviyoProfileAttributeKey.REGION)
        assertName(KlaviyoProfileAttributeKey.COUNTRY)
        assertName(KlaviyoProfileAttributeKey.ZIP)
        assertName(KlaviyoProfileAttributeKey.IMAGE)
        assertName(KlaviyoProfileAttributeKey.CONSENT)
        assertName(KlaviyoProfileAttributeKey.APPEND)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, KlaviyoProfileAttributeKey.CUSTOM(expectedCustomKey).name)

        val custom = KlaviyoProfileAttributeKey.CUSTOM(expectedCustomKey)
        assert(custom == KlaviyoProfileAttributeKey.CUSTOM(expectedCustomKey))
        assert(custom != KlaviyoProfileAttributeKey.CUSTOM(expectedCustomKey + "1"))
    }

    @Test
    fun `Event type keys`() {
        assertName(KlaviyoEventType.OPENED_PUSH)
        assertName(KlaviyoEventType.VIEWED_PRODUCT)
        assertName(KlaviyoEventType.SEARCHED_PRODUCTS)
        assertName(KlaviyoEventType.STARTED_CHECKOUT)
        assertName(KlaviyoEventType.PLACED_ORDER)
        assertName(KlaviyoEventType.ORDERED_PRODUCT)
        assertName(KlaviyoEventType.CANCELLED_ORDER)
        assertName(KlaviyoEventType.REFUNDED_ORDER)
        assertName(KlaviyoEventType.PAID_FOR_ORDER)
        assertName(KlaviyoEventType.SUBSCRIBED_TO_BACK_IN_STOCK)
        assertName(KlaviyoEventType.SUBSCRIBED_TO_COMING_SOON)
        assertName(KlaviyoEventType.SUBSCRIBED_TO_LIST)
        assertName(KlaviyoEventType.SUCCESSFUL_PAYMENT)
        assertName(KlaviyoEventType.FAILED_PAYMENT)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, KlaviyoEventType.CUSTOM(expectedCustomKey).name)

        val custom = KlaviyoEventType.CUSTOM(expectedCustomKey)
        assert(custom == KlaviyoEventType.CUSTOM(expectedCustomKey))
        assert(custom != KlaviyoEventType.CUSTOM(expectedCustomKey + "1"))
    }

    @Test
    fun `Event keys`() {
        assertName(KlaviyoEventAttributeKey.EVENT_ID)
        assertName(KlaviyoEventAttributeKey.VALUE)

        val expectedCustomKey = Math.random().toString() + "_key"
        assertEquals(expectedCustomKey, KlaviyoEventAttributeKey.CUSTOM(expectedCustomKey).name)

        val custom = KlaviyoEventAttributeKey.CUSTOM(expectedCustomKey)
        assert(custom == KlaviyoEventAttributeKey.CUSTOM(expectedCustomKey))
        assert(custom != KlaviyoEventAttributeKey.CUSTOM(expectedCustomKey + "1"))
    }
}
