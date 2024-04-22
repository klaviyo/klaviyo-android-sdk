package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PersistentObservableStringTest : BaseTest() {

    companion object {
        const val KEY = "test_key"
    }

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `Basic set and get`() {
        var delegatedProperty by PersistentObservableString(ProfileKey.CUSTOM(KEY))
        assertNull(delegatedProperty)
        delegatedProperty = "test_value"
        assertEquals("test_value", delegatedProperty)
    }

    @Test
    fun `Reads from and writes to persistent store`() {
        dataStoreSpy.store(KEY, "value")
        var delegatedProperty by PersistentObservableString(ProfileKey.CUSTOM(KEY))
        assertEquals("value", delegatedProperty)
        delegatedProperty = "new_value"
        verify(exactly = 1) { dataStoreSpy.fetch(KEY) }
        verify(exactly = 1) { dataStoreSpy.store(KEY, "new_value") }
        assertEquals("new_value", dataStoreSpy.fetch(KEY))
    }

    @Test
    fun `Uses fallback if persistent store is empty`() {
        val delegatedProperty by PersistentObservableString(ProfileKey.CUSTOM(KEY)) { "fallback" }
        assertEquals("fallback", delegatedProperty)
        assertEquals("fallback", dataStoreSpy.fetch(KEY))
        verify(exactly = 1) { dataStoreSpy.store(KEY, "fallback") }
    }

    @Test
    fun `Invokes callback when value changes`() {
        var invoked = false
        var delegatedProperty by PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { invoked = true }
        )

        assertNull(delegatedProperty)
        assertFalse(invoked)

        delegatedProperty = "2"

        assertEquals("2", delegatedProperty)
        assertTrue(invoked)
    }

    @Test
    fun `Does not store or invoke callback when value is unchanged`() {
        dataStoreSpy.store(KEY, "value")
        var invoked = false
        var delegatedProperty by PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { invoked = true }
        )

        delegatedProperty = "value"

        assertFalse(invoked)
        verify(exactly = 1) { dataStoreSpy.store(KEY, "value") }
    }

    @Test
    fun `Whitespace is trimmed prior to validation`() {
        dataStoreSpy.store(KEY, "value")
        var invoked = false
        var delegatedProperty by PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { invoked = true }
        )

        delegatedProperty = " value "

        assertFalse(invoked)
        verify(exactly = 1) { dataStoreSpy.store(KEY, "value") }
    }

    @Test
    fun `Empty string or null is ignored by primary setter method`() {
        dataStoreSpy.store(KEY, "value")
        var invoked = false
        var delegatedProperty by PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { invoked = true }
        )

        delegatedProperty = ""
        delegatedProperty = null

        assertFalse(invoked)
        verify(exactly = 1) { dataStoreSpy.store(KEY, "value") }
    }

    @Test
    fun `Resets value without invoking callback`() {
        dataStoreSpy.store(KEY, "value")
        var invoked = false
        val property = PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { invoked = true }
        )
        val delegatedProperty by property
        property.reset()
        assertFalse(invoked)
        assertNull(delegatedProperty)
        assertNull(dataStoreSpy.fetch(KEY))
    }
}
