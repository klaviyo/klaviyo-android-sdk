package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest : BaseTest() {
    private val stubPushToken = "stub_token"
    private val pushService = KlaviyoPushService()
    private val stubMessage = mutableMapOf(
        "_k" to "",
        "title" to "",
        "body" to ""
    )

    @Before
    override fun setup() {
        super.setup()
        mockkObject(Klaviyo)
        every { Klaviyo.setPushToken(any()) } returns Klaviyo

        mockkConstructor(Notification::class)

        every { anyConstructed<Notification>().displayNotification(any()) } returns Unit
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

        verify { anyConstructed<Notification>().displayNotification(any()) }
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

        verify(inverse = true) { anyConstructed<Notification>().displayNotification(any()) }
    }

    @Test
    fun `Non-klaviyo RemoteMessage is ignored`() {
        val msg = mockk<RemoteMessage>()
        stubMessage.remove("_k")
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify(inverse = true) { anyConstructed<Notification>().displayNotification(any()) }
    }
}
