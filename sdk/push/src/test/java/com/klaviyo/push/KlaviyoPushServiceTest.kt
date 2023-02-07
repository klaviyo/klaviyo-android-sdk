package com.klaviyo.push

import android.content.Intent
import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.model.DataStore
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.push.KlaviyoPushService.Companion.PUSH_TOKEN_KEY
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest {
    private val stubPushToken = "stub_token"

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

    /**
     * Mock data store service
     */
    class InMemoryDataStore : DataStore {
        private val store: MutableMap<String, String> = mutableMapOf()

        override fun fetch(key: String): String? {
            return store[key]
        }

        override fun store(key: String, value: String) {
            store[key] = value
        }

        override fun clear(key: String) {
            store.remove(key)
        }
    }

    private lateinit var store: DataStore

    @Before
    fun setup() {
        store = InMemoryDataStore() // start every test with an empty store

        mockkObject(Klaviyo)
        mockkObject(Klaviyo.Registry)
        every { Klaviyo.Registry.dataStore } returns store
        every { Klaviyo.Registry.apiClient } returns mockk()
        every { Klaviyo.Registry.apiClient.enqueueProfile(any()) } returns Unit
        every { Klaviyo.Registry.apiClient.enqueueEvent(any(), any(), any()) } returns Unit
    }

    @Test
    fun `Verify expected BuildConfig properties`() {
        // This is also just a test coverage boost
        assert(BuildConfig() is BuildConfig)
        assert(BuildConfig.DEBUG is Boolean)
        assert(BuildConfig.LIBRARY_PACKAGE_NAME == "com.klaviyo.push")
        assert(BuildConfig.BUILD_TYPE is String)
        assert(BuildConfig.KLAVIYO_SERVER_URL is String)
    }

    @Test
    fun `getPushToken fetches from persistent store`() {
        store.store(PUSH_TOKEN_KEY, stubPushToken)

        assertEquals(KlaviyoPushService.getPushToken(), stubPushToken)
    }

    @Test
    fun `setPushToken saves to persistent store and enqueues an API call`() {
        KlaviyoPushService.setPushToken(stubPushToken)

        assertEquals(store.fetch(PUSH_TOKEN_KEY), stubPushToken)
        verify { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Opening a Klaviyo push payload enqueues an event API call`() {
        KlaviyoPushService.openedPush(stubPayload)

        verify {
            Klaviyo.Registry.apiClient.enqueueEvent(KlaviyoEventType.OPENED_PUSH, any(), any())
        }
    }

    @Test
    fun `Non-klaviyo push payload is ignored`() {
        // doesn't have _k, klaviyo tracking params
        val nonKlaviyoPayload = mapOf("other" to "3rd party push")
        KlaviyoPushService.openedPush(nonKlaviyoPayload)

        verify(inverse = true) { Klaviyo.Registry.apiClient.enqueueEvent(any(), any(), any()) }
    }

    @Test
    fun `FCM onNewToken persists the new token and enqueues API call`() {
        val pushService = KlaviyoPushService()
        pushService.onNewToken(stubPushToken)

        assertEquals(KlaviyoPushService.getPushToken(), stubPushToken)
        verify { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Handling RemoteMessage does NOT enqueue $opened_push API Call`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubPayload

        val pushService = KlaviyoPushService()
        pushService.onMessageReceived(msg)

        verify(inverse = true) { Klaviyo.Registry.apiClient.enqueueEvent(any(), any(), any()) }
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

        verify {
            Klaviyo.Registry.apiClient.enqueueEvent(KlaviyoEventType.OPENED_PUSH, any(), any())
        }
    }
}
