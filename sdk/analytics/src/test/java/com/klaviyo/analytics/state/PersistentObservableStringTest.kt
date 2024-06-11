package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        spyDataStore.store(KEY, "value")
        var delegatedProperty by PersistentObservableString(ProfileKey.CUSTOM(KEY))
        assertEquals("value", delegatedProperty)
        delegatedProperty = "new_value"
        verify(exactly = 1) { spyDataStore.fetch(KEY) }
        verify(exactly = 1) { spyDataStore.store(KEY, "new_value") }
        assertEquals("new_value", spyDataStore.fetch(KEY))
    }

    @Test
    fun `Uses fallback if persistent store is empty`() {
        val delegatedProperty by PersistentObservableString(ProfileKey.CUSTOM(KEY)) { "fallback" }
        assertEquals("fallback", delegatedProperty)
        assertEquals("fallback", spyDataStore.fetch(KEY))
        verify(exactly = 1) { spyDataStore.store(KEY, "fallback") }
    }

    @Test
    fun `Invokes callback when value changes`() {
        var invokedWithProperty: PersistentObservableProperty<String?>? = null
        var invokedWithOldValue: String? = null
        val backingProp = PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { property, oldValue ->
                invokedWithProperty = property
                invokedWithOldValue = oldValue
            }
        )

        var delegatedProperty by backingProp

        assertNull(delegatedProperty)
        assertNull(invokedWithProperty)
        assertNull(invokedWithOldValue)

        delegatedProperty = "1"

        assertEquals("1", delegatedProperty)
        assertEquals(backingProp, invokedWithProperty)
        assertNull(invokedWithOldValue)

        delegatedProperty = "2"

        assertEquals("2", delegatedProperty)
        assertEquals(backingProp, invokedWithProperty)
        assertEquals("1", invokedWithOldValue)
    }

    @Test
    fun `Invokes callback with persisted value on first change`() {
        spyDataStore.store(KEY, "abc123")
        var invokedWithOldValue: String? = null
        var delegatedProperty by PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { _, oldValue ->
                invokedWithOldValue = oldValue
            }
        )

        delegatedProperty = "xyz789"

        assertEquals("xyz789", delegatedProperty)
        assertEquals("abc123", invokedWithOldValue)
    }

    @Test
    fun `Does not store or invoke callback when value is unchanged`() {
        spyDataStore.store(KEY, "value")
        var invoked = false
        var delegatedProperty by PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { _, _ -> invoked = true }
        )

        delegatedProperty = "value"

        assertFalse(invoked)
        verify(exactly = 1) { spyDataStore.store(KEY, "value") }
    }

    @Test
    fun `Whitespace is trimmed prior to validation`() {
        spyDataStore.store(KEY, "value")
        var invoked = false
        var delegatedProperty by PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { _, _ -> invoked = true }
        )

        delegatedProperty = " value "

        assertFalse(invoked)
        verify(exactly = 1) { spyDataStore.store(KEY, "value") }
    }

    @Test
    fun `Empty string or null is ignored by primary setter method`() {
        spyDataStore.store(KEY, "value")
        var invoked = false
        var delegatedProperty by PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { _, _ -> invoked = true }
        )

        delegatedProperty = ""
        delegatedProperty = null

        assertFalse(invoked)
        verify(exactly = 1) { spyDataStore.store(KEY, "value") }
    }

    @Test
    fun `Resets value without invoking callback`() {
        spyDataStore.store(KEY, "value")
        var invoked = false
        val property = PersistentObservableString(
            ProfileKey.CUSTOM(KEY),
            onChanged = { _, _ -> invoked = true }
        )
        val delegatedProperty by property
        property.reset()
        assertFalse(invoked)
        assertNull(delegatedProperty)
        assertNull(spyDataStore.fetch(KEY))
    }
}
