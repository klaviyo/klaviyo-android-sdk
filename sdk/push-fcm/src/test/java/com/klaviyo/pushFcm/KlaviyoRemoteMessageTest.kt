package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.BODY_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.INTENDED_SEND_TIME_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.KEY_VALUE_PAIRS_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.TITLE_KEY
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.hasKlaviyoKeyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.intendedSendTime
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.keyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.shouldSchedule
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

    @Test
    fun `Test intendedSendTime parsing with valid UTC time`() {
        val msg = mockk<RemoteMessage>()
        val validTimeString = "2025-12-31T12:00:00Z"
        val messageWithTime = stubMessage.toMutableMap().apply {
            put(INTENDED_SEND_TIME_KEY, validTimeString)
        }
        every { msg.data } returns messageWithTime

        val intendedTime = msg.intendedSendTime
        assert(intendedTime != null)
    }

    @Test
    fun `Test intendedSendTime parsing with invalid time format`() {
        val msg = mockk<RemoteMessage>()
        val invalidTimeString = "not-a-date"
        val messageWithInvalidTime = stubMessage.toMutableMap().apply {
            put(INTENDED_SEND_TIME_KEY, invalidTimeString)
        }
        every { msg.data } returns messageWithInvalidTime

        val intendedTime = msg.intendedSendTime
        assert(intendedTime == null)
    }

    @Test
    fun `Test shouldSchedule with future time`() {
        val msg = mockk<RemoteMessage>()
        val currentTime = TIME
        val futureTime = currentTime + 3600000 // 1 hour in the future

        // Format the future time as ISO 8601
        val futureTimeString = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            java.util.Locale.ENGLISH
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(futureTime))

        val messageWithFutureTime = stubMessage.toMutableMap().apply {
            put(INTENDED_SEND_TIME_KEY, futureTimeString)
        }

        // We use the staticClock from BaseTest which is already mocked in setup()
        every { staticClock.currentTimeMillis() } returns currentTime
        every { msg.data } returns messageWithFutureTime

        assert(msg.shouldSchedule)
    }

    @Test
    fun `Test shouldSchedule with past time`() {
        val msg = mockk<RemoteMessage>()
        val currentTime = TIME
        val pastTime = currentTime - 3600000 // 1 hour in the past

        // Format the past time as ISO 8601
        val pastTimeString = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            java.util.Locale.ENGLISH
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(pastTime))

        val messageWithPastTime = stubMessage.toMutableMap().apply {
            put(INTENDED_SEND_TIME_KEY, pastTimeString)
        }

        // We use the staticClock from BaseTest which is already mocked in setup()
        every { staticClock.currentTimeMillis() } returns currentTime
        every { msg.data } returns messageWithPastTime

        assert(!msg.shouldSchedule)
    }

    @Test
    fun `Test shouldSchedule with no intended time`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(!msg.shouldSchedule)
    }
}
