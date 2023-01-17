package com.klaviyo.coresdk.model

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.helpers.BaseTest
import com.klaviyo.coresdk.model.SharedPreferencesDataStore.KLAVIYO_PREFS_NAME
import io.mockk.called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SharedPreferencesDataStoreTest : BaseTest() {
    private val preferenceMock = mockk<SharedPreferences>()
    private val editorMock = mockk<SharedPreferences.Editor>()

    @Before
    override fun setup() {
        Klaviyo.initialize(API_KEY, contextMock)
    }

    private fun withPreferenceMock() {
        every {
            contextMock.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE)
        } returns preferenceMock
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
        withPreferenceMock()
        withWriteStringMock("key", "value")

        SharedPreferencesDataStore.store(key = "key", value = "value")

        verifyAll {
            contextMock.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE)
            preferenceMock.edit()
            editorMock.putString("key", "value")
            editorMock.apply()
        }
    }

    @Test
    fun `reading string uses Klaviyo preferences`() {
        val expectedString = "123"

        withPreferenceMock()
        withReadStringMock("key", "", expectedString)

        val actualString = Klaviyo.Registry.dataStore.fetch(key = "key")

        assertEquals(expectedString, actualString)
        verifyAll {
            contextMock.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE)
            preferenceMock.getString("key", "")
            editorMock wasNot called
        }
    }
}
