package com.klaviyo.inbox

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.core.Registry
import com.klaviyo.core.model.DataStore
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PreferencesResetMarkerStoreTest : BaseTest() {

    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()
    private val store by lazy { PreferencesResetMarkerStore(Registry.dataStore(mockContext)) }

    @Before
    override fun setup() {
        super.setup()
        every { mockContext.applicationContext } returns mockContext
        every {
            mockContext.getSharedPreferences(
                "KlaviyoSDKPreferences",
                Context.MODE_PRIVATE
            )
        } returns preferences
        every { preferences.edit() } returns editor
    }

    @After
    override fun cleanup() {
        Registry.unregister<DataStore>()
        super.cleanup()
    }

    @Test
    fun `a pending generation is written through a commit`() {
        every { editor.putString(PreferencesResetMarkerStore.KEY, "7") } returns editor
        every { editor.commit() } returns true

        assertTrue(store.commit(7L))

        verify { editor.putString(PreferencesResetMarkerStore.KEY, "7") }
        verify { editor.commit() }
        verify(exactly = 0) { editor.apply() }
    }

    @Test
    fun `a commit that does not reach storage reports failure`() {
        every { editor.putString(any(), any()) } returns editor
        every { editor.commit() } returns false

        assertFalse(store.commit(3L))
    }

    @Test
    fun `clearing the marker is committed`() {
        every { editor.remove(PreferencesResetMarkerStore.KEY) } returns editor
        every { editor.commit() } returns true

        assertTrue(store.commit(null))

        verify { editor.remove(PreferencesResetMarkerStore.KEY) }
        verify(exactly = 1) { editor.commit() }
    }

    @Test
    fun `the persisted generation is read back`() {
        every { preferences.getString(PreferencesResetMarkerStore.KEY, null) } returns "4"

        assertEquals(4L, store.load() ?: -1L)
    }

    @Test
    fun `an absent marker reads as none`() {
        every { preferences.getString(PreferencesResetMarkerStore.KEY, null) } returns null

        assertNull(store.load())
    }

    @Test(expected = IllegalStateException::class)
    fun `a malformed marker is unreadable rather than absent`() {
        every { preferences.getString(PreferencesResetMarkerStore.KEY, null) } returns "garbage"

        store.load()
    }

    @Test(expected = IllegalStateException::class)
    fun `a marker that cannot be read throws so the inbox can stay closed`() {
        every { preferences.getString(PreferencesResetMarkerStore.KEY, null) } throws
            IllegalStateException("preferences unavailable")

        store.load()
    }

    @Test
    fun `a marker write that throws reports failure`() {
        every { preferences.edit() } throws IllegalStateException("preferences unavailable")

        assertFalse(store.commit(1L))
        assertTrue(store.lastFailure is IllegalStateException)
    }

    @Test
    fun `a later commit that fails without throwing reports no stale cause`() {
        every { preferences.edit() } throws IllegalStateException("preferences unavailable")
        assertFalse(store.commit(1L))

        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.commit() } returns false
        assertFalse(store.commit(2L))

        assertNull(store.lastFailure)
    }

    @Test
    fun `a successful commit clears an earlier failure`() {
        every { preferences.edit() } throws IllegalStateException("preferences unavailable")
        assertFalse(store.commit(1L))

        every { preferences.edit() } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.commit() } returns true
        assertTrue(store.commit(null))

        assertNull(store.lastFailure)
    }
}
