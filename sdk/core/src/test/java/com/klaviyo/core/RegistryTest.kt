package com.klaviyo.core

import com.klaviyo.core.config.Log
import com.klaviyo.fixtures.Logger
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RegistryTest {

    private interface TestDependency
    private interface TestLazyDependency
    private interface TestWrongDependency
    private interface TestMissingDependency

    @Before
    fun setup() {
        Registry.register<Log>(spyk(Logger()))
    }

    @Test
    fun `Registers a dynamic dependency`() {
        val dep = object : TestDependency {}
        Registry.register<TestDependency>(dep)
        assertEquals(dep, Registry.get<TestDependency>())
    }

    @Test
    fun `Registers a dynamic dependency lazily`() {
        var callCount = 0

        Registry.register<TestLazyDependency> {
            callCount++
            object : TestLazyDependency {}
        }

        assertEquals(0, callCount)
        Registry.get<TestLazyDependency>()
        assertEquals(1, callCount)
        Registry.get<TestLazyDependency>()
        assertEquals(1, callCount)
    }

    @Test(expected = InvalidRegistration::class)
    fun `Throws when dependency is wrong type`() {
        Registry.register<TestWrongDependency> { object {} }
        Registry.get<TestWrongDependency>()
    }

    @Test(expected = MissingConfig::class)
    fun `Throws MissingConfig if SDK is uninitialized`() {
        Registry.config
    }

    @Test(expected = MissingRegistration::class)
    fun `Throws MissingRegistration for unregistered service`() {
        Registry.get<TestMissingDependency>()
    }
}
