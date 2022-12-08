package com.klaviyo.push

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils
import com.klaviyo.push.KlaviyoPushService.Companion.PUSH_TOKEN_PREFERENCE_KEY
import io.mockk.*
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest {
    private val contextMock = mockk<Context>()
    private val preferenceMock = mockk<SharedPreferences>()
    private val editorMock = mockk<SharedPreferences.Editor>()

    @Before
    fun setup() {
        KlaviyoConfig.Builder()
            .apiKey("Fake_Key")
            .applicationContext(contextMock)
            .build()
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

    @Test
    fun `Fetches current push token successfully`() {
        val pushToken = "TK1"

        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withReadStringMock(PUSH_TOKEN_PREFERENCE_KEY, "", pushToken)

        val actualToken = KlaviyoPushService.getCurrentPushToken()

        assertEquals(pushToken, actualToken)
    }

    @Test
    fun `Appends a new push token to customer properties`() {
        val pushToken = "TK1"

        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withWriteStringMock(PUSH_TOKEN_PREFERENCE_KEY, pushToken)

        mockkObject(Klaviyo)
        mockkObject(KlaviyoPreferenceUtils)
        every { Klaviyo.identify(any()) } returns Unit

        val pushService = KlaviyoPushService()
        pushService.onNewToken(pushToken)

        verifyAll {
            contextMock.getSharedPreferences("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
            preferenceMock.edit()
            editorMock.putString(PUSH_TOKEN_PREFERENCE_KEY, pushToken)
            editorMock.apply()
            Klaviyo.identify(any())
        }
    }
}