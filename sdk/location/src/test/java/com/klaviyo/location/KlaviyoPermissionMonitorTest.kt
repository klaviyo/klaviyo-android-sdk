package com.klaviyo.location

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class KlaviyoPermissionMonitorTest : BaseTest() {

    private lateinit var permissionMonitor: KlaviyoPermissionMonitor

    @Before
    override fun setup() {
        super.setup()
        mockkStatic(ActivityCompat::class)
        mockkStatic(ContextCompat::class)
    }

    @After
    override fun cleanup() {
        unmockkStatic(ActivityCompat::class)
        unmockkStatic(ContextCompat::class)
        Registry.unregister<PermissionMonitor>()
        Registry.unregister<LocationManager>()
        super.cleanup()
    }

    // Helper to mock permission state
    private fun mockPermissions(
        fineLocation: Boolean,
        backgroundLocation: Boolean = true
    ) {
        every {
            ActivityCompat.checkSelfPermission(
                any(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } returns if (fineLocation) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED

        every {
            ActivityCompat.checkSelfPermission(
                any(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } returns if (backgroundLocation) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }

    // Helper to capture lifecycle observer
    private fun captureLifecycleObserver(): ActivityObserver {
        val observerSlot = slot<ActivityObserver>()
        verify { mockLifecycleMonitor.onActivityEvent(capture(observerSlot)) }
        return observerSlot.captured
    }

    @Test
    fun `locationPermissionMonitor registry extension returns existing instance`() {
        // Pre-register a mock PermissionMonitor
        val mockMonitor = mockk<PermissionMonitor>()
        Registry.register<PermissionMonitor> { mockMonitor }

        // Access via registry extension
        val result = Registry.locationPermissionMonitor

        // Should return the existing mock
        assertSame(mockMonitor, result)
    }

    @Test
    fun `locationPermissionMonitor registry extension creates and registers new instance if none exists`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)

        // Verify no instance exists
        assertEquals(null, Registry.getOrNull<PermissionMonitor>())

        // Access via registry extension
        val result = Registry.locationPermissionMonitor

        // Should create and register a new instance
        assertNotNull(result)
        assertTrue(result is KlaviyoPermissionMonitor)
        assertSame(result, Registry.get<PermissionMonitor>())
    }

    @Test
    fun `permission state is captured initially when permissions are granted`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)

        permissionMonitor = KlaviyoPermissionMonitor()

        assertTrue(permissionMonitor.permissionState)
    }

    @Test
    fun `permission state is captured initially when permissions are denied`() {
        mockPermissions(fineLocation = false, backgroundLocation = false)

        permissionMonitor = KlaviyoPermissionMonitor()

        assertFalse(permissionMonitor.permissionState)
    }

    @Test
    fun `permission state is captured as false when exception occurs during initialization`() {
        // Mock an exception during permission check
        every {
            ActivityCompat.checkSelfPermission(mockContext, any())
        } throws SecurityException("Permission check failed")

        permissionMonitor = KlaviyoPermissionMonitor()

        assertFalse(permissionMonitor.permissionState)
    }

    @Test
    fun `onPermissionChanged registers lifecycle observer when first observer is added`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        val observer: PermissionObserver = {}

        permissionMonitor.onPermissionChanged(callback = observer)

        // Should register lifecycle observer
        verify(exactly = 1) { mockLifecycleMonitor.onActivityEvent(any()) }
    }

    @Test
    fun `onPermissionChanged does not register additional lifecycle observers for subsequent observers`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        val observer1: PermissionObserver = {}
        val observer2: PermissionObserver = {}

        permissionMonitor.onPermissionChanged(callback = observer1)
        permissionMonitor.onPermissionChanged(callback = observer2)

        // Should only register lifecycle observer once
        verify(exactly = 1) { mockLifecycleMonitor.onActivityEvent(any()) }
    }

    @Test
    fun `onPermissionChanged with unique=false allows duplicate observers`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        var callCount = 0
        val observer: PermissionObserver = { callCount++ }

        // Register same observer multiple times with unique=false
        permissionMonitor.onPermissionChanged(false, observer)
        permissionMonitor.onPermissionChanged(false, observer)
        permissionMonitor.onPermissionChanged(false, observer)

        val lifecycleObserver = captureLifecycleObserver()

        // Change permission state to trigger notification
        mockPermissions(fineLocation = false, backgroundLocation = false)
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))

        // Should be called 3 times (once per registration)
        assertEquals(3, callCount)
    }

    @Test
    fun `onPermissionChanged with unique=true prevents duplicate observers`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        var callCount = 0
        val observer: PermissionObserver = { callCount++ }

        // Register same observer multiple times with unique=true
        permissionMonitor.onPermissionChanged(true, observer)
        permissionMonitor.onPermissionChanged(true, observer)
        permissionMonitor.onPermissionChanged(true, observer)

        val lifecycleObserver = captureLifecycleObserver()

        // Change permission state to trigger notification
        mockPermissions(fineLocation = false, backgroundLocation = false)
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))

        // Should be called only once (duplicates were prevented)
        assertEquals(1, callCount)
    }

    @Test
    fun `offPermissionChanged unregisters lifecycle observer when last observer is removed`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        val observer: PermissionObserver = {}
        permissionMonitor.onPermissionChanged(callback = observer)

        permissionMonitor.offPermissionChanged(observer)

        // Should unregister lifecycle observer
        verify { mockLifecycleMonitor.offActivityEvent(any()) }
    }

    @Test
    fun `offPermissionChanged does not unregister lifecycle observer if other observers remain`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        val observer1: PermissionObserver = {}
        val observer2: PermissionObserver = {}

        permissionMonitor.onPermissionChanged(callback = observer1)
        permissionMonitor.onPermissionChanged(callback = observer2)
        permissionMonitor.offPermissionChanged(observer1)

        // Should not unregister lifecycle observer since observer2 still exists
        verify(exactly = 0) { mockLifecycleMonitor.offActivityEvent(any()) }
    }

    @Test
    fun `permissions are checked when app resumes`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        val observer: PermissionObserver = mockk(relaxed = true)
        permissionMonitor.onPermissionChanged(callback = observer)

        // Capture lifecycle observer and simulate app resume
        val lifecycleObserver = captureLifecycleObserver()

        // Change permission state
        mockPermissions(fineLocation = false, backgroundLocation = false)

        // Simulate resumed event
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))

        // Observer should be notified with new state
        verify { observer(false) }
    }

    @Test
    fun `permissions are not checked on non-resumed activity events`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        val observer: PermissionObserver = mockk(relaxed = true)
        permissionMonitor.onPermissionChanged(callback = observer)

        val lifecycleObserver = captureLifecycleObserver()

        // Simulate non-resumed events
        lifecycleObserver(ActivityEvent.Started(mockActivity))
        lifecycleObserver(ActivityEvent.Paused(mockActivity))
        lifecycleObserver(ActivityEvent.Stopped(mockActivity))

        // Observer should not be notified
        verify(exactly = 0) { observer(any()) }
    }

    @Test
    fun `observers are only notified when permission state actually changes`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        var notificationCount = 0
        val observer: PermissionObserver = { notificationCount++ }
        permissionMonitor.onPermissionChanged(callback = observer)

        val lifecycleObserver = captureLifecycleObserver()

        // Simulate resumed with same permission state
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))
        assertEquals(0, notificationCount)

        // Now change permission state
        mockPermissions(fineLocation = false, backgroundLocation = false)
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))
        assertEquals(1, notificationCount)

        // Resume again with same state (still denied)
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))
        assertEquals(1, notificationCount) // Should not increment

        // Change back to granted
        mockPermissions(fineLocation = true, backgroundLocation = true)
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))
        assertEquals(2, notificationCount)
    }

    @Test
    fun `multiple observers are all notified when permission state changes`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        var observer1Called = false
        var observer2Called = false
        var observer3Called = false

        permissionMonitor.onPermissionChanged { observer1Called = true }
        permissionMonitor.onPermissionChanged { observer2Called = true }
        permissionMonitor.onPermissionChanged { observer3Called = true }

        val lifecycleObserver = captureLifecycleObserver()

        // Change permission state
        mockPermissions(fineLocation = false, backgroundLocation = false)
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))

        // All observers should be notified
        assertTrue(observer1Called)
        assertTrue(observer2Called)
        assertTrue(observer3Called)
    }

    @Test
    fun `hasGeofencePermissions returns true when both fine location and background location are granted on API 29+`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 29)
        mockPermissions(fineLocation = true, backgroundLocation = true)

        val result = KlaviyoPermissionMonitor.hasGeofencePermissions(mockContext)

        assertTrue(result)
    }

    @Test
    fun `hasGeofencePermissions returns false when fine location is denied on API 29+`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 29)
        mockPermissions(fineLocation = false, backgroundLocation = true)

        val result = KlaviyoPermissionMonitor.hasGeofencePermissions(mockContext)

        assertFalse(result)
    }

    @Test
    fun `hasGeofencePermissions returns false when background location is denied on API 29+`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 29)
        mockPermissions(fineLocation = true, backgroundLocation = false)

        val result = KlaviyoPermissionMonitor.hasGeofencePermissions(mockContext)

        assertFalse(result)
    }

    @Test
    fun `hasGeofencePermissions returns false when both permissions are denied on API 29+`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 29)
        mockPermissions(fineLocation = false, backgroundLocation = false)

        val result = KlaviyoPermissionMonitor.hasGeofencePermissions(mockContext)

        assertFalse(result)
    }

    @Test
    fun `hasGeofencePermissions returns true with only fine location on API 28 (background not required)`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 28)
        mockPermissions(fineLocation = true, backgroundLocation = false)

        val result = KlaviyoPermissionMonitor.hasGeofencePermissions(mockContext)

        // Background location is not required on API < 29, so should return true
        assertTrue(result)
    }

    @Test
    fun `hasGeofencePermissions returns false when fine location is denied on API 28`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 28)
        mockPermissions(fineLocation = false, backgroundLocation = true)

        val result = KlaviyoPermissionMonitor.hasGeofencePermissions(mockContext)

        assertFalse(result)
    }

    @Test
    fun `hasGeofencePermissions uses Registry context by default`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 29)
        mockPermissions(fineLocation = true, backgroundLocation = true)

        // Call without context parameter
        val result = KlaviyoPermissionMonitor.hasGeofencePermissions()

        assertTrue(result)
        verify {
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    @Test
    fun `permission state change is logged`() {
        mockPermissions(fineLocation = true, backgroundLocation = true)
        permissionMonitor = KlaviyoPermissionMonitor()

        val observer: PermissionObserver = {}
        permissionMonitor.onPermissionChanged(callback = observer)

        val lifecycleObserver = captureLifecycleObserver()

        // Change permission state
        mockPermissions(fineLocation = false, backgroundLocation = false)
        lifecycleObserver(ActivityEvent.Resumed(mockActivity))

        // Should debug log the change
        verify { spyLog.debug(any()) }
    }

    @Test
    fun `hasGeofencePermissions on API 30 requires background location`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 30)
        mockPermissions(fineLocation = true, backgroundLocation = false)

        val result = KlaviyoPermissionMonitor.hasGeofencePermissions(mockContext)

        assertFalse(result)
    }

    @Test
    fun `hasGeofencePermissions on API 33 requires background location`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 33)
        mockPermissions(fineLocation = true, backgroundLocation = false)

        val result = KlaviyoPermissionMonitor.hasGeofencePermissions(mockContext)

        assertFalse(result)
    }
}
