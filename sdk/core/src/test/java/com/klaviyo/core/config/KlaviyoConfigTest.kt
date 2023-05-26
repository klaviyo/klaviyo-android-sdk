package com.klaviyo.core.config

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

internal class KlaviyoConfigTest : BaseTest() {

    private val mockPackageManager = mockk<PackageManager>()
    private val mockPackageManagerFlags = mockk<PackageManager.PackageInfoFlags>()
    private val mockVersionCode = 123
    private val mockPackageInfo = mockk<PackageInfo>().apply {
        requestedPermissions = arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
        )
        packageName = "Mock Package Name"
        versionName = "Mock Version Name"
        every { longVersionCode } returns mockVersionCode.toLong()
        versionCode = mockVersionCode
    }
    private val mockApplicationLabel = "Mock Application Label"

    @Test
    fun `Is registered service`() = assert(Registry.configBuilder is KlaviyoConfig.Builder)

    override fun setup() {
        super.setup()
        mockkStatic(PackageManager.PackageInfoFlags::class)
        every { PackageManager.PackageInfoFlags.of(any()) } returns mockPackageManagerFlags
        every { contextMock.packageManager } returns mockPackageManager
        every { contextMock.packageName } returns BuildConfig.LIBRARY_PACKAGE_NAME
        every {
            mockPackageManager.getPackageInfo(
                BuildConfig.LIBRARY_PACKAGE_NAME,
                mockPackageManagerFlags
            )
        } returns mockPackageInfo
        every { mockPackageManager.getApplicationLabel(mockApplicationInfo) } returns mockApplicationLabel
        every { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, any<Int>()) } returns mockPackageInfo
    }

    @Test
    fun `KlaviyoConfig Builder sets variables successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .baseUrl("fakeurl")
            .debounceInterval(1)
            .networkTimeout(2)
            .networkFlushInterval(1, NetworkMonitor.NetworkType.Wifi)
            .networkFlushInterval(3, NetworkMonitor.NetworkType.Cell)
            .networkFlushInterval(6, NetworkMonitor.NetworkType.Offline)
            .networkFlushDepth(4)
            .networkMaxRetries(5)
            .build()

        assertEquals(API_KEY, KlaviyoConfig.apiKey)
        assertEquals(contextMock, KlaviyoConfig.applicationContext)
        assertEquals("fakeurl", KlaviyoConfig.baseUrl)
        assertEquals(1, KlaviyoConfig.debounceInterval)
        assertEquals(2, KlaviyoConfig.networkTimeout)
        assertEquals(
            1,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Wifi.position]
        )
        assertEquals(
            3,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Cell.position]
        )
        assertEquals(
            6,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Offline.position]
        )
        assertEquals(4, KlaviyoConfig.networkFlushDepth)
        assertEquals(5, KlaviyoConfig.networkMaxRetries)
    }

    @Test
    fun `KlaviyoConfig Builder missing variables uses default values successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .build()

        assertEquals(API_KEY, KlaviyoConfig.apiKey)
        assertEquals(100, KlaviyoConfig.debounceInterval)
        assertEquals(10_000, KlaviyoConfig.networkTimeout)
        assertEquals(
            10_000,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Wifi.position]
        )
        assertEquals(
            30_000,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Cell.position]
        )
        assertEquals(
            60_000,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Offline.position]
        )
        assertEquals(25, KlaviyoConfig.networkFlushDepth)
        assertEquals(4, KlaviyoConfig.networkMaxRetries)
    }

    @Test
    fun `KlaviyoConfig Builder rejects bad values and uses default values`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .debounceInterval(-5000)
            .networkTimeout(-5000)
            .networkFlushInterval(-5000, NetworkMonitor.NetworkType.Wifi)
            .networkFlushInterval(-5000, NetworkMonitor.NetworkType.Cell)
            .networkFlushInterval(-5000, NetworkMonitor.NetworkType.Offline)
            .networkFlushDepth(-10)
            .networkMaxRetries(-10)
            .build()

        assertEquals(100, KlaviyoConfig.debounceInterval)
        assertEquals(10_000, KlaviyoConfig.networkTimeout)
        assertEquals(
            10_000,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Wifi.position]
        )
        assertEquals(
            30_000,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Cell.position]
        )
        assertEquals(
            60_000,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Offline.position]
        )
        assertEquals(25, KlaviyoConfig.networkFlushDepth)
        assertEquals(4, KlaviyoConfig.networkMaxRetries)

        // Each bad call should have generated an error log
        verify(exactly = 7) { logSpy.error(any(), null) }
    }

    @Test(expected = MissingAPIKey::class)
    fun `KlaviyoConfig Builder missing API key throws expected exception`() {
        KlaviyoConfig.Builder()
            .applicationContext(contextMock)
            .build()
    }

    @Test(expected = MissingContext::class)
    fun `KlaviyoConfig Builder missing application context throws exception`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .build()
    }

    @Test(expected = MissingPermission::class)
    fun `KlaviyoConfig Builder throws exception when context is missing required permissions`() {
        mockPackageInfo.requestedPermissions = arrayOf()
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .build()
    }

    @Test
    fun `getPackageInfoCompat detects platform properly`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 33)
        mockPackageManager.getPackageInfoCompat(
            contextMock.packageName,
            PackageManager.GET_PERMISSIONS
        )
        verify {
            mockPackageManager.getPackageInfo(
                BuildConfig.LIBRARY_PACKAGE_NAME,
                mockPackageManagerFlags
            )
        }

        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 23)
        mockPackageManager.getPackageInfoCompat(
            contextMock.packageName,
            PackageManager.GET_PERMISSIONS
        )
        verify { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, any<Int>()) }
    }
}
