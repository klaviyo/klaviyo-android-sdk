package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest : BaseTest() {
    private val stubPushToken = "stub_token"
    private val pushService = KlaviyoPushService()

    @Before
    override fun setup() {
        super.setup()
        mockkObject(Klaviyo)
        every { Klaviyo.setPushToken(any()) } returns Klaviyo
    }

    @Test
    fun `FCM onNewToken persists the new token and enqueues API call`() {
        pushService.onNewToken(stubPushToken)
        verify { Klaviyo.setPushToken(stubPushToken) }
    }

    @Test
    fun `Handling RemoteMessage does NOT enqueue $opened_push API Call`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns mockk()

        pushService.onMessageReceived(msg)

        verify(inverse = true) { Klaviyo.handlePush(any()) }
    }
}
