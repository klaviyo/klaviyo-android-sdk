package com.klaviyo.core.model

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.core.Registry
import com.klaviyo.core.model.SharedPreferencesDataStore.KLAVIYO_PREFS_NAME
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkObject
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
        every { editorMock.remove(key) } returns editorMock
        every { editorMock.apply() } returns Unit
    }

    @Test
    fun `Is registered service`() {
        unmockkObject(Registry)
        assertEquals(SharedPreferencesDataStore, Registry.dataStore)
    }

    @Test
    fun `Writing string uses Klaviyo preferences`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)

        SharedPreferencesDataStore.store(stubKey, stubValue)

        verify { contextMock.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE) }
        verify { preferenceMock.edit() }
        verify { editorMock.putString(stubKey, stubValue) }
        verify { editorMock.apply() }

        // And verify log output
        verify { logSpy.debug("$stubKey=$stubValue") }
    }

    @Test
    fun `Removing key uses Klaviyo preferences`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)

        SharedPreferencesDataStore.clear(stubKey)

        verify { contextMock.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE) }
        verify { preferenceMock.edit() }
        verify { editorMock.remove(stubKey) }
        verify { editorMock.apply() }

        // And verify log output
        verify { logSpy.debug("$stubKey=null") }
    }

    @Test
    fun `Reading string uses Klaviyo preferences`() {
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
