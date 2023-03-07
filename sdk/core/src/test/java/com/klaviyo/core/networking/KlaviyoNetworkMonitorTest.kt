package com.klaviyo.core.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

internal class KlaviyoNetworkMonitorTest : BaseTest() {
    private val connectivityManagerMock: ConnectivityManager = mockk()
    private val networkMock: Network = mockk()
    private val capabilitiesMock: NetworkCapabilities = mockk()
    private val mockBuilder: NetworkRequest.Builder = mockk()
    private val mockRequest: NetworkRequest = mockk()

    private companion object {
        // Can only instantiate this once, because KlaviyoNetworkMonitorTest is a static object
        var netCallbackSlot = slot<ConnectivityManager.NetworkCallback>()
    }

    override fun setup() {
        super.setup()

        // Mock connectivityManager for spot check and for callbacks
        every { contextMock.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManagerMock
        every { contextMock.getSystemService(ConnectivityManager::class.java) } returns connectivityManagerMock
        every { connectivityManagerMock.activeNetwork } returns networkMock
        every { connectivityManagerMock.getNetworkCapabilities(null) } returns null
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns capabilitiesMock
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        mockkConstructor(NetworkRequest.Builder::class)
        every { NetworkRequest.Builder().addCapability(any()) } returns mockBuilder
        every { mockBuilder.build() } returns mockRequest
        every {
            connectivityManagerMock.requestNetwork(
                mockRequest,
                capture(netCallbackSlot)
            )
        } returns mockk()
    }

    @Test
    fun `Is registered service`() {
        unmockkObject(Registry)
        assertEquals(KlaviyoNetworkMonitor, Registry.networkMonitor)
    }

    @Test
    fun `Network online if connectivityManager's active network is online`() {
        assert(KlaviyoNetworkMonitor.isNetworkConnected())
    }

    @Test
    fun `Maps transport capabilities to network types correctly`() {
        // No network capabilities = offline
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns null
        every { capabilitiesMock.hasTransport(any()) } returns false
        assertEquals(NetworkMonitor.NetworkType.Offline, KlaviyoNetworkMonitor.getNetworkType())

        // We'll treat internet without a particular transport method as cell
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns capabilitiesMock
        assertEquals(NetworkMonitor.NetworkType.Cell, KlaviyoNetworkMonitor.getNetworkType())

        every { capabilitiesMock.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        assertEquals(NetworkMonitor.NetworkType.Cell, KlaviyoNetworkMonitor.getNetworkType())

        every { capabilitiesMock.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        assertEquals(NetworkMonitor.NetworkType.Wifi, KlaviyoNetworkMonitor.getNetworkType())
    }

    @Test
    fun `Network offline if connectivityManager's active network is offline`() {
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        assert(!KlaviyoNetworkMonitor.isNetworkConnected())
    }

    @Test
    fun `Network offline if connectivityManager has no active network`() {
        every { connectivityManagerMock.activeNetwork } returns null
        assert(!KlaviyoNetworkMonitor.isNetworkConnected())
    }

    @Test
    fun `Network offline if activeNetwork has no capabilities`() {
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns null
        assert(!KlaviyoNetworkMonitor.isNetworkConnected())
    }

    @Test
    fun `Network change observer is invoked with current network status when network changes`() {
        var expectedNetworkConnection = true
        var callCount = 0

        KlaviyoNetworkMonitor.onNetworkChange {
            assert(it == expectedNetworkConnection)
            callCount++
        }

        assert(netCallbackSlot.isCaptured) // attaching a listener should have initialized the network callback

        expectedNetworkConnection = true
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        netCallbackSlot.captured.onAvailable(mockk())

        expectedNetworkConnection = false
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        netCallbackSlot.captured.onLost(mockk())
        netCallbackSlot.captured.onUnavailable()

        expectedNetworkConnection = true
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        netCallbackSlot.captured.onCapabilitiesChanged(mockk(), mockk())
        netCallbackSlot.captured.onLinkPropertiesChanged(mockk(), mockk())

        expectedNetworkConnection = false
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        netCallbackSlot.captured.onLost(mockk())

        assertEquals(6, callCount)
    }

    @Test
    fun `Network changes are logged`() {
        KlaviyoNetworkMonitor // Initialize, which would normally just happen when app launches
        assert(netCallbackSlot.isCaptured)

        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        netCallbackSlot.captured.onAvailable(mockk())
        verify { logSpy.info("Network available") }

        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        netCallbackSlot.captured.onUnavailable()
        verify { logSpy.info("Network unavailable") }
    }

    @Test
    fun `Network change observer can be removed`() {
        var callCount = 0
        val observer: NetworkObserver = { callCount++ }
        val observer2: NetworkObserver = { callCount++ }

        KlaviyoNetworkMonitor.onNetworkChange(observer)
        KlaviyoNetworkMonitor.onNetworkChange(observer2)

        netCallbackSlot.captured.onAvailable(mockk())
        assertEquals(2, callCount)

        KlaviyoNetworkMonitor.offNetworkChange(observer)
        netCallbackSlot.captured.onAvailable(mockk())
        assertEquals(3, callCount)

        KlaviyoNetworkMonitor.offNetworkChange(observer) // calling it twice doesn't result in an error
        KlaviyoNetworkMonitor.onNetworkChange(observer) // it can be re-added
        netCallbackSlot.captured.onAvailable(mockk())
        assertEquals(5, callCount)
    }
}
