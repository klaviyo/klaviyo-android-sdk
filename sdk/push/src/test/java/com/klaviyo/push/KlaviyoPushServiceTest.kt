package com.klaviyo.push

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.push.KlaviyoPushService.Companion.PUSH_TOKEN_PREFERENCE_KEY
import io.mockk.every
import io.mockk.mockk
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest {
    private val contextMock = mockk<Context>()
    private val preferenceMock = mockk<SharedPreferences>()

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
}