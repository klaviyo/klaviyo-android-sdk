package com.klaviyo.coresdk.utils

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.KlaviyoConfig
import io.mockk.called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KlaviyoPreferenceUtilsTest {
    private val contextMock = mockk<Context>()
    private val preferenceMock = mockk<SharedPreferences>()
    private val editorMock = mockk<SharedPreferences.Editor>()

    @Before
    fun setup() {
        clearAllMocks()
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
    fun `writing string uses Klaviyo preferences`() {
        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withWriteStringMock("key", "value")

        KlaviyoPreferenceUtils.store(key = "key", value = "value")

        verifyAll {
            contextMock.getSharedPreferences("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
            preferenceMock.edit()
            editorMock.putString("key", "value")
            editorMock.apply()
        }
    }

    @Test
    fun `reading string uses Klaviyo preferences`() {
        val expectedString = "123"

        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withReadStringMock("key", "", expectedString)

        val actualString = KlaviyoPreferenceUtils.fetch(key = "key")

        assertEquals(expectedString, actualString)
        verifyAll {
            contextMock.getSharedPreferences("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
            preferenceMock.getString("key", "")
            editorMock wasNot called
        }
    }

    @Test
    fun `do not create new UUID if one exists in shared preferences`() {
        withPreferenceMock("KlaviyoSDKPreferences", Context.MODE_PRIVATE)
        withReadStringMock(KlaviyoPreferenceUtils.KLAVIYO_UUID_KEY, "", "123")

        val uuid = KlaviyoPreferenceUtils.readOrGenerateUUID()

        assertEquals("123", uuid)
    }
}
