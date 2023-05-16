package com.klaviyo.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RegistryTest {

    private interface TestDependency
    private interface TestLazyDependency
    private interface TesWrongDependency
    private interface TestMissingDependency

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
        Registry.register<TesWrongDependency> { object {} }
        Registry.get<TesWrongDependency>()
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
