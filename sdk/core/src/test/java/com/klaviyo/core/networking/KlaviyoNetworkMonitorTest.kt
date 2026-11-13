package com.klaviyo.core.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
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
        every { connectivityManagerMock.registerDefaultNetworkCallback(capture(netCallbackSlot)) } just runs
        every {
            connectivityManagerMock.registerNetworkCallback(mockRequest, capture(netCallbackSlot))
        } just runs
        every {
            connectivityManagerMock.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        } just runs
        netCallbackSlot.clear()
        setSdkInt(Build.VERSION_CODES.N)
    }

    @After
    override fun cleanup() {
        super.cleanup()
        clearAllMocks()

        // Reset singleton state so the next test starts clean: no leaked observers or registration
        val observers = KlaviyoNetworkMonitor::class.java.getDeclaredField("networkChangeObservers")
        observers.isAccessible = true
        (observers.get(KlaviyoNetworkMonitor) as MutableCollection<*>).clear()
        val field = KlaviyoNetworkMonitor::class.java.getDeclaredField("isCallbackRegistered")
        field.isAccessible = true
        field.setBoolean(KlaviyoNetworkMonitor, false)
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
        KlaviyoNetworkMonitor.onNetworkChange {} // attaching an observer registers the callback
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

    @Test
    fun `Thrown network exception doesn't crash app and registration is retried`() {
        val exception = SecurityException("missing permission")
        every { connectivityManagerMock.registerDefaultNetworkCallback(any()) } throws exception

        var callCount = 0
        val observer: NetworkObserver = { callCount++ }
        KlaviyoNetworkMonitor.onNetworkChange(observer)

        verify {
            spyLog.warning(
                "Failed to attach network monitor, degraded performance around requests may occur",
                exception
            )
        }
        assertEquals(0, callCount)

        // Next observer retries registration, which now succeeds
        every { connectivityManagerMock.registerDefaultNetworkCallback(capture(netCallbackSlot)) } just runs
        val observer2: NetworkObserver = { callCount++ }
        KlaviyoNetworkMonitor.onNetworkChange(observer2)
        assert(netCallbackSlot.isCaptured)
        KlaviyoNetworkMonitor.offNetworkChange(observer)
        KlaviyoNetworkMonitor.offNetworkChange(observer2)
    }

    @Test
    fun `Registers default network callback on API 24+`() {
        val observer: NetworkObserver = {}
        KlaviyoNetworkMonitor.onNetworkChange(observer)

        verify(exactly = 1) { connectivityManagerMock.registerDefaultNetworkCallback(any()) }
        verify(exactly = 0) {
            connectivityManagerMock.registerNetworkCallback(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        }
        KlaviyoNetworkMonitor.offNetworkChange(observer)
    }

    @Test
    fun `Falls back to registerNetworkCallback on API 23`() {
        setSdkInt(Build.VERSION_CODES.M)
        val observer: NetworkObserver = {}
        KlaviyoNetworkMonitor.onNetworkChange(observer)

        verify(exactly = 0) { connectivityManagerMock.registerDefaultNetworkCallback(any()) }
        verify(exactly = 1) {
            connectivityManagerMock.registerNetworkCallback(
                mockRequest,
                any<ConnectivityManager.NetworkCallback>()
            )
        }
        assert(netCallbackSlot.isCaptured)
        KlaviyoNetworkMonitor.offNetworkChange(observer)
    }

    @Test
    fun `Registers only once for multiple observers`() {
        val observer: NetworkObserver = {}
        val observer2: NetworkObserver = {}
        KlaviyoNetworkMonitor.onNetworkChange(observer)
        KlaviyoNetworkMonitor.onNetworkChange(observer2)

        verify(exactly = 1) { connectivityManagerMock.registerDefaultNetworkCallback(any()) }
        KlaviyoNetworkMonitor.offNetworkChange(observer)
        KlaviyoNetworkMonitor.offNetworkChange(observer2)
    }

    @Test
    fun `Unregisters callback when last observer is removed and re-registers on next`() {
        val observer: NetworkObserver = {}
        val observer2: NetworkObserver = {}
        KlaviyoNetworkMonitor.onNetworkChange(observer)
        KlaviyoNetworkMonitor.onNetworkChange(observer2)

        KlaviyoNetworkMonitor.offNetworkChange(observer)
        verify(exactly = 0) {
            connectivityManagerMock.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        }

        KlaviyoNetworkMonitor.offNetworkChange(observer2)
        verify(exactly = 1) {
            connectivityManagerMock.unregisterNetworkCallback(
                netCallbackSlot.captured
            )
        }

        KlaviyoNetworkMonitor.onNetworkChange(observer)
        verify(exactly = 2) { connectivityManagerMock.registerDefaultNetworkCallback(any()) }
        KlaviyoNetworkMonitor.offNetworkChange(observer)
    }

    @Test
    fun `Unregister failure doesn't crash and allows re-registration`() {
        every {
            connectivityManagerMock.unregisterNetworkCallback(
                any<ConnectivityManager.NetworkCallback>()
            )
        } throws IllegalArgumentException("not registered")
        val observer: NetworkObserver = {}
        KlaviyoNetworkMonitor.onNetworkChange(observer)
        KlaviyoNetworkMonitor.offNetworkChange(observer)
        verify { spyLog.warning(any(), any()) }

        KlaviyoNetworkMonitor.onNetworkChange(observer)
        verify(exactly = 2) { connectivityManagerMock.registerDefaultNetworkCallback(any()) }
        KlaviyoNetworkMonitor.offNetworkChange(observer)
    }

    private fun setSdkInt(level: Int) =
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), level)
}
