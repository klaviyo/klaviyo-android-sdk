package com.klaviyo.core.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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

    @Before
    override fun setup() {
        super.setup()

        // Mock connectivityManager for spot check and for callbacks
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManagerMock
        every { mockContext.getSystemService(ConnectivityManager::class.java) } returns connectivityManagerMock
        every { connectivityManagerMock.activeNetwork } returns networkMock
        every { connectivityManagerMock.getNetworkCapabilities(null) } returns null
        every { connectivityManagerMock.getNetworkCapabilities(networkMock) } returns capabilitiesMock
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
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

    @After
    override fun cleanup() {
        super.cleanup()
        clearAllMocks()

        // Reset KlaviyoNetworkMonitor's networkRequest to allow re-initialization in subsequent tests
        val field = KlaviyoNetworkMonitor::class.java.getDeclaredField("networkRequest")
        val originalAccessibility = field.isAccessible // Store original accessibility
        field.isAccessible = true
        field.set(KlaviyoNetworkMonitor, null) // Set the lateinit var's backing field to null
        field.isAccessible = originalAccessibility // Restore original accessibility
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
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
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
        val observer: NetworkObserver = {
            assert(it == expectedNetworkConnection)
            callCount++
        }
        KlaviyoNetworkMonitor.onNetworkChange(observer)

        assert(netCallbackSlot.isCaptured) // attaching a listener should have initialized the network callback

        expectedNetworkConnection = true
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        netCallbackSlot.captured.onAvailable(mockk())

        expectedNetworkConnection = false
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        netCallbackSlot.captured.onLost(mockk())
        netCallbackSlot.captured.onUnavailable()

        expectedNetworkConnection = true
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        netCallbackSlot.captured.onLinkPropertiesChanged(mockk(), mockk())

        expectedNetworkConnection = false
        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        netCallbackSlot.captured.onLost(mockk())

        assertEquals(5, callCount)
        KlaviyoNetworkMonitor.offNetworkChange(observer)
    }

    @Test
    fun `Network changes are logged`() {
        KlaviyoNetworkMonitor // Initialize, which would normally just happen when app launches
        assert(netCallbackSlot.isCaptured)

        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        netCallbackSlot.captured.onAvailable(mockk())
        verify { spyLog.verbose(any()) }

        every { capabilitiesMock.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        netCallbackSlot.captured.onUnavailable()
        verify { spyLog.verbose(any()) }
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

    @Test()
    fun `Concurrent modification exception doesn't get thrown on concurrent observer access`() = runTest {
        val observer: NetworkObserver = { Thread.sleep(6) }

        KlaviyoNetworkMonitor.onNetworkChange(observer)

        val job = launch(Dispatchers.IO) {
            netCallbackSlot.captured.onAvailable(mockk())
        }

        val job2 = launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            KlaviyoNetworkMonitor.offNetworkChange(observer)
        }

        job.start()
        job2.start()
    }

    @Test()
    fun `Thrown network exception doesn't crash app`() {
        val exception = SecurityException("missing permission")
        every {
            connectivityManagerMock.requestNetwork(
                mockRequest,
                any<ConnectivityManager.NetworkCallback>()
            )
        } throws exception

        var callCount = 0
        val observer: NetworkObserver = {
            callCount++
        }
        KlaviyoNetworkMonitor.onNetworkChange(observer)

        verify {
            spyLog.warning(
                "Failed to attach network monitor, degraded performance around requests may occur",
                exception
            )
        }
        assertEquals(callCount, 0)
    }
}
