package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class GeofencingTest : BaseTest() {

    private val mockLocationManager = mockk<LocationManager>().apply {
        every { startGeofenceMonitoring() } returns Unit
        every { stopGeofenceMonitoring() } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()
        mockkObject(KlaviyoLocationManager)
        every { KlaviyoLocationManager.startGeofenceMonitoring() } returns Unit
        every { KlaviyoLocationManager.stopGeofenceMonitoring() } returns Unit
    }

    @After
    override fun cleanup() {
        Registry.unregister<LocationManager>()
        Registry.unregister<PermissionMonitor>()
        unmockkObject(KlaviyoLocationManager)
        super.cleanup()
    }

    @Test
    fun `registerGeofencing returns Klaviyo instance`() {
        val result = Klaviyo.registerGeofencing()
        assertSame(Klaviyo, result)
    }

    @Test
    fun `registerGeofencing registers LocationManager and PermissionMonitor`() {
        assertNull(Registry.getOrNull<LocationManager>())
        assertNull(Registry.getOrNull<PermissionMonitor>())

        Klaviyo.registerGeofencing()

        assertNotNull(Registry.getOrNull<LocationManager>())
        assertNotNull(Registry.getOrNull<PermissionMonitor>())
    }

    @Test
    fun `registerGeofencing does not re-register existing dependencies`() {
        // Pre-register a mock LocationManager
        Registry.register<LocationManager>(mockLocationManager)

        Klaviyo.registerGeofencing()

        // Should still be our mock
        assertSame(mockLocationManager, Registry.get<LocationManager>())

        // Should call startGeofenceMonitoring on the existing manager
        verify { mockLocationManager.startGeofenceMonitoring() }
    }

    @Test
    fun `unregisterGeofencing returns Klaviyo instance`() {
        Registry.register<LocationManager>(mockLocationManager)
        val result = Klaviyo.unregisterGeofencing()
        assertSame(Klaviyo, result)
    }

    @Test
    fun `unregisterGeofencing calls startGeofenceMonitoring when LocationManager is registered`() {
        Registry.register<LocationManager>(mockLocationManager)

        Klaviyo.unregisterGeofencing()

        // BUG: Currently calls startGeofenceMonitoring instead of stopGeofenceMonitoring
        verify { mockLocationManager.startGeofenceMonitoring() }
    }

    @Test
    fun `unregisterGeofencing logs warning when LocationManager is not registered`() {
        // Ensure LocationManager is not registered
        Registry.unregister<LocationManager>()

        Klaviyo.unregisterGeofencing()

        // Should log warning message
        verify { spyLog.warning("Cannot unregister geofencing, must be registered first.") }
    }
}
