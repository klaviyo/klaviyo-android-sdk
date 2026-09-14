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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SharedPreferencesDataStoreTest : BaseTest() {
    private val preferenceMock = mockk<SharedPreferences>()
    private val editorMock = mockk<SharedPreferences.Editor>()
    private val stubKey = "key" + Math.random().toString()
    private val stubValue = "value" + Math.random().toString()

    private fun withPreferenceMock() {
        every {
            mockContext.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE)
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

        verify { mockContext.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE) }
        verify { preferenceMock.edit() }
        verify { editorMock.putString(stubKey, stubValue) }
        verify { editorMock.apply() }

        // And verify log output
        verify { spyLog.verbose("$stubKey=$stubValue") }
    }

    @Test
    fun `Fetch or create uses Klaviyo preferences`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)
        every { preferenceMock.getString(stubKey, null) } returns null

        SharedPreferencesDataStore.fetchOrCreate(stubKey) { stubValue }

        verify { mockContext.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE) }
        verify { preferenceMock.getString(stubKey, null) }
        verify { preferenceMock.edit() }
        verify { editorMock.putString(stubKey, stubValue) }
        verify { editorMock.apply() }

        // And verify log output for writing
        verify { spyLog.verbose("$stubKey=$stubValue") }
    }

    @Test
    fun `Store observers concurrency modification test`() = runTest {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)
        val observer: StoreObserver = { _, _ -> Thread.sleep(6) }

        SharedPreferencesDataStore.onStoreChange(observer)

        val job = launch(Dispatchers.IO) {
            SharedPreferencesDataStore.clear(stubKey)
        }

        val job2 = launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                Thread.sleep(8)
            }
            SharedPreferencesDataStore.offStoreChange(observer)
        }

        job.start()
        job2.start()
    }

    @Test
    fun `Removing key uses Klaviyo preferences`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)

        SharedPreferencesDataStore.clear(stubKey)

        verify { mockContext.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE) }
        verify { preferenceMock.edit() }
        verify { editorMock.remove(stubKey) }
        verify { editorMock.apply() }

        // And verify log output
        verify { spyLog.verbose("$stubKey=null") }
    }

    @Test
    fun `Reading string uses Klaviyo preferences`() {
        val expectedString = "123" + Math.random().toString()

        withPreferenceMock()
        every { preferenceMock.getString(stubKey, null) } returns expectedString

        val actualString = SharedPreferencesDataStore.fetch(key = stubKey)

        assertEquals(expectedString, actualString)
        verify { mockContext.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE) }
        verify { preferenceMock.getString(stubKey, null) }
        verify(inverse = true) { editorMock.apply() }
    }

    @Test
    fun `Clearing multiple keys uses a single edit and apply`() {
        val keys = listOf("key-a", "key-b", "key-c")
        withPreferenceMock()
        every { preferenceMock.edit() } returns editorMock
        keys.forEach { every { editorMock.remove(it) } returns editorMock }
        every { editorMock.apply() } returns Unit

        val observer = mockk<StoreObserver>(relaxed = true)
        SharedPreferencesDataStore.onStoreChange(observer)

        try {
            SharedPreferencesDataStore.clear(keys)
        } finally {
            SharedPreferencesDataStore.offStoreChange(observer)
        }

        // One edit/apply pair regardless of how many keys are removed
        verify(exactly = 1) { preferenceMock.edit() }
        verify(exactly = 1) { editorMock.apply() }
        keys.forEach { key -> verify(exactly = 1) { editorMock.remove(key) } }

        // Observers are still notified per key
        keys.forEach { key -> verify(exactly = 1) { observer(key, null) } }
    }

    @Test
    fun `Clearing an empty key collection does not open preferences`() {
        withPreferenceMock()

        SharedPreferencesDataStore.clear(emptyList())

        verify(exactly = 0) { preferenceMock.edit() }
    }
}
