package com.klaviyo.location

import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test

class CompanyObserverTest : BaseTest() {
    private val observerSlot = slot<StateChangeObserver>()
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { onStateChange(capture(observerSlot)) } returns Unit
        every { apiKey } returns "new_company_id"
    }

    private val mockLocationManager = mockk<LocationManager>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(stateMock)
        Registry.register<LocationManager>(mockLocationManager)
    }

    @After
    override fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<LocationManager>()
        super.cleanup()
    }

    @Test
    fun `startObserver attaches to state change`() {
        CompanyObserver().startObserver()
        assert(observerSlot.isCaptured)
    }

    @Test
    fun `observer stops clears and restarts geofence monitoring when company ID changes`() {
        val observer = CompanyObserver()
        observer.startObserver()

        assert(observerSlot.isCaptured)
        observerSlot.captured.invoke(StateChange.ApiKey("old_company_id"))

        verify(exactly = 1) { mockLocationManager.stopGeofenceMonitoring() }
        verify(exactly = 1) { mockLocationManager.clearStoredGeofences() }
        verify(exactly = 1) { mockLocationManager.startGeofenceMonitoring() }
    }

    @Test
    fun `observer calls geofence methods in correct order when company ID changes`() {
        val observer = CompanyObserver()
        observer.startObserver()

        assert(observerSlot.isCaptured)
        observerSlot.captured.invoke(StateChange.ApiKey("old_company_id"))

        verifyOrder {
            mockLocationManager.stopGeofenceMonitoring()
            mockLocationManager.clearStoredGeofences()
            mockLocationManager.startGeofenceMonitoring()
        }
    }

    @Test
    fun `observer ignores other state changes`() {
        val observer = CompanyObserver()
        observer.startObserver()

        assert(observerSlot.isCaptured)
        observerSlot.captured.invoke(
            StateChange.KeyValue(ProfileKey.CUSTOM("test_key"), "test_value")
        )

        verify(inverse = true) { mockLocationManager.stopGeofenceMonitoring() }
        verify(inverse = true) { mockLocationManager.clearStoredGeofences() }
        verify(inverse = true) { mockLocationManager.startGeofenceMonitoring() }
    }

    @Test
    fun `stopObserver removes the observer from state change listeners`() {
        val observer = CompanyObserver()
        observer.startObserver()
        observer.stopObserver()

        verify(exactly = 1) { stateMock.offStateChange(observerSlot.captured) }
    }

    @Test
    fun `startObserver called multiple times only registers once`() {
        val observer = CompanyObserver()
        observer.startObserver()
        observer.startObserver()
        observer.startObserver()

        verify(exactly = 1) { stateMock.onStateChange(any()) }
    }

    @Test
    fun `stopObserver called without start does nothing`() {
        val observer = CompanyObserver()
        observer.stopObserver()

        verify(inverse = true) { stateMock.offStateChange(any()) }
    }

    @Test
    fun `can restart observer after stopping`() {
        val observer = CompanyObserver()
        observer.startObserver()
        observer.stopObserver()
        observer.startObserver()

        verify(exactly = 2) { stateMock.onStateChange(any()) }
        verify(exactly = 1) { stateMock.offStateChange(any()) }
    }
}
