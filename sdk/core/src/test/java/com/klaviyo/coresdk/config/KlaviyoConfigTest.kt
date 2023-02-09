package com.klaviyo.coresdk.config

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.klaviyo.coresdk.BaseTest
import com.klaviyo.coresdk.BuildConfig
import com.klaviyo.coresdk.Registry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class KlaviyoConfigTest : BaseTest() {

    private val mockPackageManager = mockk<PackageManager>()
    private val mockPackageManagerFlags = mockk<PackageManager.PackageInfoFlags>()
    private val mockPackageInfo = mockk<PackageInfo>().apply {
        requestedPermissions = arrayOf(Manifest.permission.ACCESS_NETWORK_STATE)
    }

    @Test
    fun `Is registered service`() = assert(Registry.configBuilder is KlaviyoConfig.Builder)

    override fun setup() {
        mockkStatic(PackageManager.PackageInfoFlags::class)
        every { PackageManager.PackageInfoFlags.of(any()) } returns mockPackageManagerFlags
        every { contextMock.packageManager } returns mockPackageManager
        every { contextMock.packageName } returns BuildConfig.LIBRARY_PACKAGE_NAME
        every { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, mockPackageManagerFlags) } returns mockPackageInfo
        every { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, any<Int>()) } returns mockPackageInfo
    }

    @Test
    fun `Verify expected BuildConfig properties`() {
        // KlaviyoConfig should be our interface with BuildConfig,
        // but also this is also just a nice test coverage boost
        assert(BuildConfig() is BuildConfig)
        assert(BuildConfig.DEBUG is Boolean)
        assertEquals("com.klaviyo.coresdk", BuildConfig.LIBRARY_PACKAGE_NAME)
        assert(BuildConfig.BUILD_TYPE is String)
        assert(BuildConfig.KLAVIYO_SERVER_URL is String)
    }

    @Test
    fun `KlaviyoConfig Builder sets variables successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .debounceInterval(123)
            .networkTimeout(1000)
            .networkFlushInterval(10000)
            .networkFlushDepth(10)
            .build()

        assertEquals(API_KEY, KlaviyoConfig.apiKey)
        assertEquals(contextMock, KlaviyoConfig.applicationContext)
        assertEquals(123, KlaviyoConfig.debounceInterval)
        assertEquals(1000, KlaviyoConfig.networkTimeout)
        assertEquals(10000, KlaviyoConfig.networkFlushInterval)
        assertEquals(10, KlaviyoConfig.networkFlushDepth)
    }

    @Test
    fun `KlaviyoConfig Builder missing variables uses default values successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .build()

        assertEquals(API_KEY, KlaviyoConfig.apiKey)
        assertEquals(500, KlaviyoConfig.networkTimeout)
        assertEquals(60000, KlaviyoConfig.networkFlushInterval)
        assertEquals(20, KlaviyoConfig.networkFlushDepth)
    }

    @Test
    fun `KlaviyoConfig Builder negative variables uses default values successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .debounceInterval(-5000)
            .networkTimeout(-5000)
            .networkFlushInterval(-5000)
            .networkFlushDepth(-10)
            .build()

        assertNotEquals(-5000, KlaviyoConfig.debounceInterval)
        assertNotEquals(-5000, KlaviyoConfig.networkTimeout)
        assertNotEquals(-1000, KlaviyoConfig.networkFlushInterval)
        assertNotEquals(-10, KlaviyoConfig.networkFlushDepth)
    }

    @Test(expected = MissingAPIKey::class)
    fun `KlaviyoConfig Builder missing API key throws expected exception`() {
        KlaviyoConfig.Builder()
            .applicationContext(contextMock)
            .networkTimeout(500)
            .networkFlushInterval(60000)
            .networkFlushDepth(20)
            .build()
    }

    @Test(expected = MissingContext::class)
    fun `KlaviyoConfig Builder missing application context throws exception`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .networkTimeout(500)
            .networkFlushInterval(60000)
            .networkFlushDepth(20)
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
        mockPackageManager.getPackageInfoCompat(contextMock.packageName, PackageManager.GET_PERMISSIONS)
        verify { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, mockPackageManagerFlags) }

        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 23)
        mockPackageManager.getPackageInfoCompat(contextMock.packageName, PackageManager.GET_PERMISSIONS)
        verify { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, any<Int>()) }
    }

    @Test
    fun `Clock uses proper date format`() {
        val regex7 = "^\\d{4}(-\\d\\d(-\\d\\d(T\\d\\d:\\d\\d(:\\d\\d)?(\\.\\d+)?(([+-]\\d\\d:*\\d\\d)|Z)?)?)?)?\$".toRegex()
        val dateString = SystemClock.currentTimeAsString()
        assert(regex7.matches(dateString))
    }

    @Test
    fun `Clock can perform or cancel a delayed task`() {
        var counter = 0

        SystemClock.schedule(5L) { counter++ }
        SystemClock.schedule(5L) { counter++ }.cancel()
        assertEquals(0, counter)
        Thread.sleep(10L)
        assertEquals(1, counter)
        Thread.sleep(10L)
        assertEquals(1, counter)
    }
}
