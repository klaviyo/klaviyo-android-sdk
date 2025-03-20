package com.klaviyo.core.config

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.R
import com.klaviyo.core.Registry
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class KlaviyoConfigTest : BaseTest() {

    private val mockPackageManagerFlags = mockk<PackageManager.PackageInfoFlags>()
    private val mockVersionCode = 123
    private val mockApplicationLabel = "Mock Application Label"

    @Suppress("DEPRECATION")
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

    @Before
    override fun setup() {
        super.setup()
        mockkStatic(PackageManager.PackageInfoFlags::class)
        every { PackageManager.PackageInfoFlags.of(any()) } returns mockPackageManagerFlags
        every { mockContext.packageManager } returns mockPackageManager
        every { mockContext.packageName } returns BuildConfig.LIBRARY_PACKAGE_NAME
        every { mockContext.resources } returns mockk {
            every { getString(R.string.klaviyo_sdk_name_override) } returns "android"
            every { getString(R.string.klaviyo_sdk_version_override) } returns "9.9.9"
        }
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
    fun `Is registered service`() = assert(Registry.configBuilder is KlaviyoConfig.Builder)

    @Test
    fun `KlaviyoConfig Builder sets variables successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(mockContext)
            .baseUrl("fakeurl")
            .debounceInterval(1)
            .networkTimeout(2)
            .networkFlushInterval(1, NetworkMonitor.NetworkType.Wifi)
            .networkFlushInterval(3, NetworkMonitor.NetworkType.Cell)
            .networkFlushInterval(6, NetworkMonitor.NetworkType.Offline)
            .networkFlushDepth(4)
            .networkMaxAttempts(5)
            .networkMaxRetryInterval(7)
            .baseCdnUrl("spider-water.com")
            .assetSource("1738")
            .build()

        assertEquals(API_KEY, KlaviyoConfig.apiKey)
        assertEquals(mockContext, KlaviyoConfig.applicationContext)
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
        assertEquals(5, KlaviyoConfig.networkMaxAttempts)
        assertEquals(7, KlaviyoConfig.networkMaxRetryInterval)
        assertEquals("android", KlaviyoConfig.sdkName)
        assertEquals("9.9.9", KlaviyoConfig.sdkVersion)
        assertEquals("spider-water.com", KlaviyoConfig.baseCdnUrl)
        assertEquals("1738", KlaviyoConfig.assetSource)
    }

    @Test
    fun `KlaviyoConfig Builder missing variables uses default values successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(mockContext)
            .build()

        assertEquals(API_KEY, KlaviyoConfig.apiKey)
        assertEquals(100, KlaviyoConfig.debounceInterval)
        assertEquals(10_000, KlaviyoConfig.networkTimeout)
        assertEquals(
            10_000L,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Wifi.position]
        )
        assertEquals(
            30_000L,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Cell.position]
        )
        assertEquals(
            60_000L,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Offline.position]
        )
        assertEquals(25, KlaviyoConfig.networkFlushDepth)
        assertEquals(50, KlaviyoConfig.networkMaxAttempts)
        assertEquals(180_000L, KlaviyoConfig.networkMaxRetryInterval)
        assertEquals("android", KlaviyoConfig.sdkName)
        assertEquals("9.9.9", KlaviyoConfig.sdkVersion)
    }

    @Test
    fun `KlaviyoConfig Builder rejects bad values and uses default values`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(mockContext)
            .debounceInterval(-5000)
            .networkTimeout(-5000)
            .networkFlushInterval(-5000, NetworkMonitor.NetworkType.Wifi)
            .networkFlushInterval(-5000, NetworkMonitor.NetworkType.Cell)
            .networkFlushInterval(-5000, NetworkMonitor.NetworkType.Offline)
            .networkFlushDepth(-10)
            .networkMaxAttempts(-10)
            .networkMaxRetryInterval(-1)
            .build()

        assertEquals(100, KlaviyoConfig.debounceInterval)
        assertEquals(10_000, KlaviyoConfig.networkTimeout)
        assertEquals(
            10_000L,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Wifi.position]
        )
        assertEquals(
            30_000L,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Cell.position]
        )
        assertEquals(
            60_000,
            KlaviyoConfig.networkFlushIntervals[NetworkMonitor.NetworkType.Offline.position]
        )
        assertEquals(25, KlaviyoConfig.networkFlushDepth)
        assertEquals(50, KlaviyoConfig.networkMaxAttempts)
        assertEquals(180_000, KlaviyoConfig.networkMaxRetryInterval)
        assertEquals("android", KlaviyoConfig.sdkName)
        assertEquals("9.9.9", KlaviyoConfig.sdkVersion)
        // Each bad call should have generated an error log
        verify(exactly = 8) { spyLog.error(any(), null) }
    }

    @Test(expected = MissingAPIKey::class)
    fun `KlaviyoConfig Builder missing API key throws expected exception`() {
        KlaviyoConfig.Builder()
            .applicationContext(mockContext)
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
            .applicationContext(mockContext)
            .build()
    }

    @Test
    fun `getPackageInfoCompat detects platform properly`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 33)
        mockPackageManager.getPackageInfoCompat(
            mockContext.packageName,
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
            mockContext.packageName,
            PackageManager.GET_PERMISSIONS
        )
        verify { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, any<Int>()) }
    }
}
