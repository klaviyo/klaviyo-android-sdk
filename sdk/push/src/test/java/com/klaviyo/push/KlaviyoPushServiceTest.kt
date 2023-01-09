package com.klaviyo.push

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import com.klaviyo.push.KlaviyoPushService.Companion.PUSH_TOKEN_PREFERENCE_KEY
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verifyAll
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest {
    private val contextMock = mockk<Context>()
    private val preferenceMock = mockk<SharedPreferences>()
    private val editorMock = mockk<SharedPreferences.Editor>()
    private val stubPushToken = "TK1"

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

    @Before
    fun setup() {
        Klaviyo.initialize(
            apiKey = "Fake_Key",
            applicationContext = contextMock
        )
    }

    private fun withPreferenceMock(preferenceName: String, mode: Int) {
        every { contextMock.getSharedPreferences(preferenceName, mode) } returns preferenceMock
    }

    private fun withWriteStringMock(key: String, value: String) {
        every { preferenceMock.edit() } returns editorMock
        every { editorMock.putString(key, value) } returns editorMock
        every { editorMock.apply() } returns Unit
    }

    private fun withReadStringMock(key: String, default: String?, string: String) {
        every { preferenceMock.getString(key, default) } returns string
    }

    private fun withKlaviyoMock() {
        mockkObject(Klaviyo)
        mockkObject(KlaviyoPreferenceUtils)
        every { Klaviyo.setProfile(any()) } returns Klaviyo
        every { Klaviyo.createEvent(any(), any(), any()) } returns Unit
    }

    @Test
    fun `Fetches current push token successfully`() {
        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withReadStringMock(PUSH_TOKEN_PREFERENCE_KEY, "", stubPushToken)

        val actualToken = KlaviyoPushService.getPushToken()

        assertEquals(stubPushToken, actualToken)
    }

    @Test
    fun `Appends a new push token to customer properties`() {
        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withWriteStringMock(PUSH_TOKEN_PREFERENCE_KEY, stubPushToken)
        withKlaviyoMock()

        KlaviyoPushService.setPushToken(stubPushToken)

        verifyAll {
            contextMock.getSharedPreferences("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
            preferenceMock.edit()
            editorMock.putString(PUSH_TOKEN_PREFERENCE_KEY, stubPushToken)
            editorMock.apply()
            Klaviyo.setProfile(any())
        }
    }

    @Test
    fun `Klaviyo push payload triggers an opened push event`() {
        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withReadStringMock(PUSH_TOKEN_PREFERENCE_KEY, "", "token")
        withKlaviyoMock()

        KlaviyoPushService.openedPush(stubPayload)

        verifyAll {
            Klaviyo.createEvent(any(), any(), any())
        }
    }

    @Test
    fun `Non-klaviyo push payload is ignored`() {
        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withReadStringMock(PUSH_TOKEN_PREFERENCE_KEY, "", "token")
        withKlaviyoMock()

        KlaviyoPushService.openedPush(
            mapOf("other" to "3rd party push") // doesn't have _k, klaviyo tracking params
        )

        verifyAll(true) {
            Klaviyo.createEvent(any(), any(), any())
        }
    }

    @Test
    fun `FCM methods invoke SDK`() {
        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withWriteStringMock(PUSH_TOKEN_PREFERENCE_KEY, stubPushToken)
        withReadStringMock(PUSH_TOKEN_PREFERENCE_KEY, "", stubPushToken)
        withKlaviyoMock()

        val pushService = KlaviyoPushService()
        KlaviyoPushService.setPushToken(stubPushToken)

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubPayload
        pushService.onMessageReceived(msg)

        verifyAll {
            Klaviyo.setProfile(any())
            Klaviyo.createEvent(any(), any(), any())
        }
    }
}
