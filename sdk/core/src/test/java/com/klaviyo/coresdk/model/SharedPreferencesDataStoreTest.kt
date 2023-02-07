package com.klaviyo.coresdk.model

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.BaseTest
import com.klaviyo.coresdk.model.SharedPreferencesDataStore.KLAVIYO_PREFS_NAME
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SharedPreferencesDataStoreTest : BaseTest() {
    private val preferenceMock = mockk<SharedPreferences>()
    private val editorMock = mockk<SharedPreferences.Editor>()
    private val stubKey = "key" + Math.random().toString()
    private val stubValue = "value" + Math.random().toString()

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

    @Test
    fun `writing string uses Klaviyo preferences`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)

        SharedPreferencesDataStore.store(stubKey, stubValue)

        verify { contextMock.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE) }
        verify { preferenceMock.edit() }
        verify { editorMock.putString(stubKey, stubValue) }
        verify { editorMock.apply() }
    }

    @Test
    fun `reading string uses Klaviyo preferences`() {
        val expectedString = "123" + Math.random().toString()

        withPreferenceMock()
        every { preferenceMock.getString(stubKey, "") } returns expectedString

        val actualString = SharedPreferencesDataStore.fetch(key = stubKey)

        assertEquals(expectedString, actualString)
        verify { contextMock.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE) }
        verify { preferenceMock.getString(stubKey, "") }
        verify(inverse = true) { editorMock.apply() }
    }
}