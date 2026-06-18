package com.klaviyo.location

import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LocationInitProviderTest : BaseTest() {

    @After
    override fun cleanup() {
        Registry.unregister<GeofencingProvider>()
        super.cleanup()
    }

    @Test
    fun `onCreate registers GeofencingProvider`() {
        val provider = LocationInitProvider()
        val result = provider.onCreate()

        assertTrue(result)
        assertTrue(Registry.isRegistered<GeofencingProvider>())
        assertNotNull(Registry.getOrNull<GeofencingProvider>())
    }
}
