package com.klaviyo.core

import com.klaviyo.core.config.Log
import com.klaviyo.fixtures.LogFixture
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RegistryTest {

    private interface TestDependency
    private interface TestLazyDependency
    private interface TestLazyOnceDependency
    private interface TestWrongDependency
    private interface TestMissingDependency

    @Before
    fun setup() {
        Registry.register<Log>(spyk(LogFixture()))
    }

    @After
    fun cleanup() {
        Registry.unregister<Log>()
        Registry.unregister<TestDependency>()
        Registry.unregister<TestLazyDependency>()
        Registry.unregister<TestLazyOnceDependency>()
        Registry.unregister<TestWrongDependency>()
        Registry.unregister<TestMissingDependency>()
    }

    @Test
    fun `Indicates registered or unregistered services`() {
        assertEquals(true, Registry.isRegistered<Log>())
        assertEquals(false, Registry.isRegistered<TestMissingDependency>())
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

    @Test
    fun `registerOnce lazily only registers a service if not already registered`() {
        var firstCallCount = 0
        var secondCallCount = 0

        // First lazy registration
        Registry.registerOnce<TestLazyOnceDependency> {
            firstCallCount++
            object : TestLazyOnceDependency {}
        }

        // Ensure the service is not initialized yet
        assertEquals(0, firstCallCount)

        // Access the service to initialize it
        val firstInstance = Registry.get<TestLazyOnceDependency>()
        assertEquals(1, firstCallCount)

        // Attempt to register a new lazy instance
        Registry.registerOnce<TestLazyOnceDependency> {
            secondCallCount++
            object : TestLazyOnceDependency {}
        }

        // Ensure the first instance is still registered
        assertEquals(firstInstance, Registry.get<TestLazyOnceDependency>())
        assertEquals(0, secondCallCount)
        assertEquals(1, firstCallCount)
    }

    @Test
    fun `getOrNull returns null for unregistered service`() {
        val result = Registry.getOrNull<TestMissingDependency>()
        assertEquals(null, result)
    }

    @Test
    fun `getOrNull returns a registered service`() {
        val dep = object : TestDependency {}
        Registry.register<TestDependency>(dep)
        val result = Registry.getOrNull<TestDependency>()
        assertEquals(dep, result)
    }

    @Test
    fun `getOrNull returns a lazily registered service`() {
        val dep = object : TestDependency {}
        Registry.register<TestDependency> { dep }
        val result = Registry.getOrNull<TestDependency>()
        assertEquals(dep, result)
    }
}
