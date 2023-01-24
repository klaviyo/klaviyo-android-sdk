package com.klaviyo.push

import android.content.Intent
import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.model.DataStore
import com.klaviyo.push.KlaviyoPushService.Companion.PUSH_TOKEN_PREFERENCE_KEY
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import io.mockk.verifyAll
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
    object InMemoryDataStore : DataStore {
        private val store: MutableMap<String, String> = mutableMapOf()

        override fun fetch(key: String): String? {
            return store[key]
        }

        override fun store(key: String, value: String) {
            store[key] = value
        }
    }

    @Before
    fun setup() {
        mockkObject(Klaviyo)
        mockkObject(Klaviyo.Registry)
        every { Klaviyo.Registry.dataStore } returns InMemoryDataStore
        every { Klaviyo.setProfile(any()) } returns Klaviyo
        every { Klaviyo.createEvent(any(), any(), any()) } returns Klaviyo
    }

    @Test
    fun `Fetches current push token successfully`() {
        InMemoryDataStore.store(PUSH_TOKEN_PREFERENCE_KEY, stubPushToken)

        val actualToken = KlaviyoPushService.getPushToken()

        assertEquals(stubPushToken, actualToken)
    }

    @Test
    fun `Appends a new push token to profile`() {
        KlaviyoPushService.setPushToken(stubPushToken)

        assertEquals(stubPushToken, InMemoryDataStore.fetch(PUSH_TOKEN_PREFERENCE_KEY))
        verify { Klaviyo.setProfile(any()) }
    }

    @Test
    fun `Klaviyo push payload triggers an opened push event`() {
        KlaviyoPushService.openedPush(stubPayload)

        verifyAll {
            Klaviyo.createEvent(any(), any(), any())
        }
    }

    @Test
    fun `Non-klaviyo push payload is ignored`() {
        KlaviyoPushService.openedPush(
            mapOf("other" to "3rd party push") // doesn't have _k, klaviyo tracking params
        )

        verifyAll(true) {
            Klaviyo.createEvent(any(), any(), any())
        }
    }

    @Test
    fun `FCM methods invoke SDK`() {
        val pushService = KlaviyoPushService()
        KlaviyoPushService.setPushToken(stubPushToken)

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubPayload
        pushService.onMessageReceived(msg)

        verifyAll {
            Klaviyo.createEvent(any(), any(), any())
        }
    }

//    @Test //TODO
    fun `Handling RemoteMessage does not trigger $opened_push`() {
        KlaviyoPushService.openedPush(
            mapOf("other" to "3rd party push") // doesn't have _k, klaviyo tracking params
        )

        verifyAll(true) {
            Klaviyo.createEvent(any(), any(), any())
        }
    }

//    @Test //TODO
    fun `Handling a push intent triggers $opened_push`() {
        val intent = mockk<Intent>()
        val bundle = mockk<Bundle>()
        every { intent.extras } returns bundle
        KlaviyoPushService.handlePush(intent)

        verifyAll {
            Klaviyo.createEvent(any(), any(), any())
        }
    }
}
