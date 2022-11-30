package com.klaviyo.coresdk.utils

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils.KLAVIYO_UUID_KEY
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class KlaviyoPreferenceUtilsTest {
    private val contextMock = mock<Context>()

    @Before
    fun setup() {
        KlaviyoConfig.Builder()
            .apiKey("Fake_Key")
            .applicationContext(contextMock)
            .build()
    }

    @Test
    fun `Do not create new UUID if one exists in shared preferences already`() {
        val sharedPreferencesMock = Mockito.mock(SharedPreferences::class.java)

        whenever(contextMock.getSharedPreferences(any(), any())).thenReturn(sharedPreferencesMock)
        whenever(sharedPreferencesMock.getString(KLAVIYO_UUID_KEY, "")).thenReturn("123")

        val uuid = KlaviyoPreferenceUtils.readOrGenerateUUID()

        assertEquals("123", uuid)
    }
}
