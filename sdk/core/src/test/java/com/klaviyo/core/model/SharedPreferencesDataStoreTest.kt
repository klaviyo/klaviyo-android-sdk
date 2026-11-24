package com.klaviyo.core.model

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.model.SharedPreferencesDataStore.Companion.KLAVIYO_PREFS_NAME
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SharedPreferencesDataStoreTest : BaseTest() {
    private val preferenceMock = mockk<SharedPreferences>()
    private val editorMock = mockk<SharedPreferences.Editor>()
    private val stubKey = "key" + Math.random().toString()
    private val stubValue = "value" + Math.random().toString()
    private val store: DataStore get() = Registry.dataStore

    @Before
    override fun setup() {
        super.setup()
        every { Registry.dataStore } answers { callOriginal() }
    }

    @After
    override fun cleanup() {
        Registry.unregister<DataStore>()
        super.cleanup()
    }

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

    private inline fun withSdkInt(sdkInt: Int, block: () -> Unit) {
        val field = Build.VERSION::class.java.getField("SDK_INT")
        val original = Build.VERSION.SDK_INT
        setFinalStatic(field, sdkInt)
        try {
            block()
        } finally {
            setFinalStatic(field, original)
        }
    }

    @Test
    fun `the property can be obtained before config and later shares its store with context`() {
        every { Registry.config } throws MissingConfig()
        every { mockContext.applicationContext } returns mockContext
        withPreferenceMock()
        every { preferenceMock.getString(stubKey, null) } returns stubValue

        val original = store
        verify(exactly = 0) { Registry.config }
        assertThrows(MissingConfig::class.java) { original.fetch(stubKey) }

        assertSame(original, Registry.dataStore(mockContext))
        verify(exactly = 0) { mockContext.getSharedPreferences(any(), any()) }
        assertEquals(stubValue, original.fetch(stubKey))
    }

    @Test
    fun `context-first access shares the store and uses the application before config`() {
        every { Registry.config } throws MissingConfig()
        val callerContext = mockk<Context>().apply {
            every { applicationContext } returns mockContext
        }
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)
        every { preferenceMock.getString(stubKey, null) } returns stubValue

        val contextualStore = Registry.dataStore(callerContext)

        assertSame(contextualStore, store)
        verify(exactly = 0) { mockContext.getSharedPreferences(any(), any()) }
        assertEquals(stubValue, store.fetch(stubKey))
        val observer = mockk<StoreObserver>(relaxed = true)
        store.onStoreChange(observer)
        contextualStore.store(stubKey, stubValue)
        verify(exactly = 1) { observer(stubKey, stubValue) }
        verify(exactly = 0) { Registry.config }
    }

    @Test
    fun `checked commits report the disk result and notify observers only on success`() {
        every { mockContext.applicationContext } returns mockContext
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)
        every { editorMock.commit() } returns true andThen true andThen false
        val observer = mockk<StoreObserver>(relaxed = true)
        store.onStoreChange(observer)

        val contextualStore = Registry.dataStore(mockContext)
        assertTrue(contextualStore.commit(stubKey, stubValue))
        assertTrue(contextualStore.commit(stubKey, null))
        assertFalse(contextualStore.commit(stubKey, stubValue))

        verify(exactly = 1) { observer(stubKey, stubValue) }
        verify(exactly = 1) { observer(stubKey, null) }
        verify(exactly = 2) { editorMock.putString(stubKey, stubValue) }
        verify(exactly = 1) { editorMock.remove(stubKey) }
        verify(exactly = 3) { editorMock.commit() }
        verify(exactly = 0) { editorMock.apply() }
    }

    @Test
    fun `legacy checked commit retries write distinct revisions with the marker`() =
        withSdkInt(Build.VERSION_CODES.N_MR1) {
            withPreferenceMock()
            withWriteStringMock(stubKey, stubValue)
            every { editorMock.putString(neq(stubKey), any()) } returns editorMock
            every { editorMock.commit() } returns false andThen true
            val observed = mutableListOf<Pair<String, String?>>()
            store.onStoreChange { key, value -> observed += key to value }

            assertFalse(store.commit(stubKey, stubValue))
            assertTrue(observed.isEmpty())
            assertTrue(store.commit(stubKey, stubValue))

            val revisions = mutableListOf<String>()
            verifySequence {
                preferenceMock.edit()
                editorMock.putString(stubKey, stubValue)
                editorMock.putString(neq(stubKey), capture(revisions))
                editorMock.commit()
                preferenceMock.edit()
                editorMock.putString(stubKey, stubValue)
                editorMock.putString(neq(stubKey), capture(revisions))
                editorMock.commit()
            }
            assertNotEquals(revisions[0], revisions[1])
            assertEquals(listOf(stubKey to stubValue), observed)
        }

    @Test
    fun `checked commits on Android O write only the requested key`() =
        withSdkInt(Build.VERSION_CODES.O) {
            withPreferenceMock()
            withWriteStringMock(stubKey, stubValue)
            every { editorMock.commit() } returns true

            assertTrue(store.commit(stubKey, stubValue))

            verifySequence {
                preferenceMock.edit()
                editorMock.putString(stubKey, stubValue)
                editorMock.commit()
            }
        }

    @Test
    fun `checked commits do not notify observers if the editor throws`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)
        val failure = IllegalStateException("Editor failed")
        every { editorMock.commit() } throws failure
        val observer = mockk<StoreObserver>(relaxed = true)
        store.onStoreChange(observer)

        assertSame(
            failure,
            assertThrows(IllegalStateException::class.java) { store.commit(stubKey, stubValue) }
        )

        verify(exactly = 0) { observer(any(), any()) }
    }

    @Test
    fun `checked commits log observer failures and continue notifying observers`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)
        every { editorMock.commit() } returns true
        val failure = IllegalStateException("Observer failed")
        val observer = mockk<StoreObserver>(relaxed = true)
        store.onStoreChange { _, _ -> throw failure }
        store.onStoreChange(observer)

        assertTrue(store.commit(stubKey, stubValue))

        verify(exactly = 1) { observer(stubKey, stubValue) }
        verify(exactly = 1) { spyLog.error(any(), failure) }
    }

    @Test
    fun `Writing string uses Klaviyo preferences`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)

        store.store(stubKey, stubValue)

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

        store.fetchOrCreate(stubKey) { stubValue }

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

        store.onStoreChange(observer)

        val job = launch(Dispatchers.IO) {
            store.clear(stubKey)
        }

        val job2 = launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                Thread.sleep(8)
            }
            store.offStoreChange(observer)
        }

        job.start()
        job2.start()
    }

    @Test
    fun `Removing key uses Klaviyo preferences`() {
        withPreferenceMock()
        withWriteStringMock(stubKey, stubValue)

        store.clear(stubKey)

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

        val actualString = store.fetch(key = stubKey)

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
        store.onStoreChange(observer)

        try {
            store.clear(keys)
        } finally {
            store.offStoreChange(observer)
        }

        // One edit/apply pair regardless of how many keys are removed
        verify(exactly = 1) { preferenceMock.edit() }
        verify(exactly = 1) { editorMock.apply() }
        keys.forEach { key -> verify(exactly = 1) { editorMock.remove(key) } }

        // Observers are still notified per key
        keys.forEach { key -> verify(exactly = 1) { observer(key, null) } }
    }

    @Test
    fun `a failing observer does not interrupt a batch clear notification`() {
        val keys = listOf("key-a", "key-b")
        withPreferenceMock()
        every { preferenceMock.edit() } returns editorMock
        every { editorMock.remove(any()) } returns editorMock
        every { editorMock.apply() } returns Unit
        val failure = IllegalStateException("Observer failed")
        store.onStoreChange { _, _ -> throw failure }
        val observed = mutableListOf<Pair<String, String?>>()
        store.onStoreChange { key, value -> observed += key to value }

        store.clear(keys)

        assertEquals(keys.map { it to null }, observed)
        verify(exactly = 2) { spyLog.error(any(), failure) }
    }

    @Test
    fun `Clearing an empty key collection does not open preferences`() {
        withPreferenceMock()

        store.clear(emptyList())

        verify(exactly = 0) {
            mockContext.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
}
