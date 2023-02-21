package com.klaviyo.push_fcm

import android.content.Intent
import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core.model.DataStore
import com.klaviyo.core_shared_tests.BaseTest
import com.klaviyo.core_shared_tests.InMemoryDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest : BaseTest() {
    private val stubPushToken = "stub_token"
    private val apiClientMock: ApiClient = mockk()

    /**
     * Stub of a push payload data property
     * the "_k" tracking properties indicates payload originated from klaviyo
     */
    private val stubPayload = mapOf(
        "body" to "Message body",
        "_k" to """{
          "Push Platform": "android",
          "$\flow": "",
          "$\message": "01GK4P5W6AV4V3APTJ727JKSKQ",
          "$\variation": "",
          "Message Name": "check_push_pipeline",
          "Message Type": "campaign",
          "c": "6U7nPA",
          "cr": "31698553996657051350694345805149781",
          "m": "01GK4P5W6AV4V3APTJ727JKSKQ",
          "t": "1671205224",
          "timestamp": "2022-12-16T15:40:24.049427+00:00",
          "x": "manual"
        }"""
    )

    private var store: DataStore = InMemoryDataStore()

    @Before
    override fun setup() {
        super.setup()
        every { Registry.dataStore } returns store
        Registry.add<ApiClient>(apiClientMock)
        every { apiClientMock.enqueuePushToken(any(), any()) } returns Unit
        every { apiClientMock.enqueueEvent(any(), any()) } returns Unit
    }

    @Test
    fun `getPushToken fetches from persistent store`() {
        store.store("push_token", stubPushToken)

        assertEquals(KlaviyoPushService.getPushToken(), stubPushToken)
    }

    @Test
    fun `setPushToken saves to persistent store and enqueues an API call`() {
        KlaviyoPushService.setPushToken(stubPushToken)

        assertEquals(store.fetch("push_token"), stubPushToken)
        verify { apiClientMock.enqueuePushToken(any(), any()) }
    }

    @Test
    fun `Opening a Klaviyo push payload enqueues an event API call`() {
        KlaviyoPushService.openedPush(stubPayload)

        verify { apiClientMock.enqueueEvent(any(), any()) }
    }

    @Test
    fun `Non-klaviyo push payload is ignored`() {
        // doesn't have _k, klaviyo tracking params
        val nonKlaviyoPayload = mapOf("other" to "3rd party push")
        KlaviyoPushService.openedPush(nonKlaviyoPayload)

        verify(inverse = true) { apiClientMock.enqueueEvent(any(), any()) }
    }

    @Test
    fun `FCM onNewToken persists the new token and enqueues API call`() {
        val pushService = KlaviyoPushService()
        pushService.onNewToken(stubPushToken)

        assertEquals(KlaviyoPushService.getPushToken(), stubPushToken)
        verify { apiClientMock.enqueuePushToken(any(), any()) }
    }

    @Test
    fun `Handling RemoteMessage does NOT enqueue $opened_push API Call`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubPayload

        val pushService = KlaviyoPushService()
        pushService.onMessageReceived(msg)

        verify(inverse = true) { apiClientMock.enqueueEvent(any(), any()) }
    }

    @Test
    fun `Handling opened push Intent enqueues $opened_push API Call`() {
        // Mocking an intent to return the stub push payload...
        val intent = mockk<Intent>()
        val bundle = mockk<Bundle>()
        var gettingKey = ""
        every { intent.extras } returns bundle
        every { bundle.keySet() } returns stubPayload.keys
        every {
            bundle.getString(
                match { s ->
                    gettingKey = s // there must be a better way to do this...
                    stubPayload.containsKey(s)
                },
                String()
            )
        } returns (stubPayload[gettingKey] ?: "")

        // Handle push intent
        KlaviyoPushService.handlePush(intent)

        verify { apiClientMock.enqueueEvent(any(), any()) }
    }
}
