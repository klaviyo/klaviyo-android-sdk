package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.BODY_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.KEY_VALUE_PAIRS_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.TITLE_KEY
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.hasKlaviyoKeyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.keyValuePairs
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Test

class KlaviyoRemoteMessageTest : BaseTest() {
    private val stubKeyValuePairs = mapOf(
        "test_key_1" to "test_value_1",
        "test_key_2" to "test_value_2",
        "test_key_3" to "test_value_3"
    )
    private val stubMessage = mutableMapOf(
        "_k" to "",
        TITLE_KEY to "test title",
        BODY_KEY to "test body",
        KEY_VALUE_PAIRS_KEY to JSONObject(stubKeyValuePairs).toString()
    )

    @Test
    fun `Test isKlaviyoMessage`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.isKlaviyoMessage)
    }

    @Test
    fun `Test isKlaviyoNotification`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.isKlaviyoNotification)
    }

    @Test
    fun `Test message is silent push`() {
        val msg = mockk<RemoteMessage>()

        stubMessage.remove("title")
        stubMessage.remove("body")
        every { msg.data } returns stubMessage

        assert(!msg.isKlaviyoNotification)
    }

    @Test
    fun `Test Key-Value Pairs Deserialization`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.hasKlaviyoKeyValuePairs)
        assert(msg.keyValuePairs == stubKeyValuePairs)
    }
}
