package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.BODY_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.KEY_VALUE_PAIRS_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.TITLE_KEY
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest : BaseTest() {
    private val stubPushToken = "stub_token"
    private val pushService = spyk(KlaviyoPushService())
    private val stubKeyValuePairs = mapOf(
        "test_key_1" to "test_value_1",
        "test_key_2" to "test_value_2",
        "test_key_3" to "test_value_3"
    )
    private val stubMessage = mutableMapOf(
        "_k" to "",
        TITLE_KEY to "",
        BODY_KEY to "",
        KEY_VALUE_PAIRS_KEY to JSONObject(stubKeyValuePairs).toString()
    )

    @Before
    override fun setup() {
        super.setup()
        // mockkStatic is required for @JvmStatic methods
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        every { Klaviyo.setPushToken(any()) } returns Klaviyo

        mockkConstructor(KlaviyoNotification::class)

        every { anyConstructed<KlaviyoNotification>().displayNotification(any()) } returns true
    }

    @Test
    fun `FCM onNewToken persists the new token and enqueues API call`() {
        pushService.onNewToken(stubPushToken)
        verify { Klaviyo.setPushToken(stubPushToken) }
    }

    @Test
    fun `A RemoteMessage with notification data is passed on to displayNotification`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify { anyConstructed<KlaviyoNotification>().displayNotification(any()) }
    }

    @Test
    fun `Handling RemoteMessage does NOT enqueue $opened_push API Call`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns mapOf(
            "_k" to "",
            "title" to "",
            "body" to ""
        )

        pushService.onMessageReceived(msg)

        verify(inverse = true) { Klaviyo.handlePush(any()) }
    }

    @Test
    fun `Silent push is not displayed`() {
        val msg = mockk<RemoteMessage>()
        stubMessage.remove("title")
        stubMessage.remove("body")
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify(inverse = true) { anyConstructed<KlaviyoNotification>().displayNotification(any()) }
    }

    @Test
    fun `Custom data handler is called`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify { pushService.onKlaviyoCustomDataMessageReceived(stubKeyValuePairs, msg) }
    }

    @Test
    fun `Non-klaviyo RemoteMessage is ignored`() {
        val msg = mockk<RemoteMessage>()
        stubMessage.remove("_k")
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify(inverse = true) { anyConstructed<KlaviyoNotification>().displayNotification(any()) }
    }
}
