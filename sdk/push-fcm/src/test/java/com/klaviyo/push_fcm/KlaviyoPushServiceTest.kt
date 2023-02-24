package com.klaviyo.push_fcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core_shared_tests.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest : BaseTest() {
    private val stubPushToken = "stub_token"
    private val apiClientMock: ApiClient = mockk()
    private val pushService = KlaviyoPushService()

    @Before
    override fun setup() {
        super.setup()
        Registry.register<ApiClient>(apiClientMock)
        every { apiClientMock.enqueuePushToken(any(), any()) } returns Unit
        every { apiClientMock.enqueueEvent(any(), any()) } returns Unit
    }

    @Test
    fun `FCM onNewToken persists the new token and enqueues API call`() {
        pushService.onNewToken(stubPushToken)

        assertEquals(Klaviyo.getPushToken(), stubPushToken)
        verify { apiClientMock.enqueuePushToken(any(), any()) }
    }

    @Test
    fun `Handling RemoteMessage does NOT enqueue $opened_push API Call`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns mockk()

        pushService.onMessageReceived(msg)

        verify(inverse = true) { apiClientMock.enqueueEvent(any(), any()) }
    }
}
