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
        Registry.add<TestDependency>(dep)
        assertEquals(dep, Registry.get<TestDependency>())
    }

    @Test
    fun `Registers a dynamic dependency lazily`() {
        var callCount = 0

        Registry.add<TestLazyDependency> {
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
        Registry.add<TesWrongDependency> { object {} }
        Registry.get<TesWrongDependency>()
    }

    @Test(expected = MissingDependency::class)
    fun `Throws when dependency is missing`() {
        Registry.get<TestMissingDependency>()
    }
}
