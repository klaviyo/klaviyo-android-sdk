package com.klaviyo.coresdk.config

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.klaviyo.coresdk.BaseTest
import com.klaviyo.coresdk.BuildConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Test

internal class KlaviyoConfigTest : BaseTest() {

    private val mockPackageManager = mockk<PackageManager>()
    private val mockPackageManagerFlags = mockk<PackageManager.PackageInfoFlags>()
    private val mockPackageInfo = mockk<PackageInfo>().apply {
        requestedPermissions = arrayOf(Manifest.permission.ACCESS_NETWORK_STATE)
    }

    override fun setup() {
        mockkStatic(PackageManager.PackageInfoFlags::class)
        every { PackageManager.PackageInfoFlags.of(any()) } returns mockPackageManagerFlags
        every { contextMock.packageManager } returns mockPackageManager
        every { contextMock.packageName } returns BuildConfig.LIBRARY_PACKAGE_NAME
        every { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, mockPackageManagerFlags) } returns mockPackageInfo
        every { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, any<Int>()) } returns mockPackageInfo
    }

    @Test
    fun `KlaviyoConfig Builder sets variables successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .networkTimeout(1000)
            .networkFlushInterval(10000)
            .networkFlushDepth(10)
            .build()

        assert(KlaviyoConfig.apiKey == API_KEY)
        assert(KlaviyoConfig.networkTimeout == 1000)
        assert(KlaviyoConfig.networkFlushInterval == 10000)
        assert(KlaviyoConfig.networkFlushDepth == 10)
    }

    @Test
    fun `KlaviyoConfig Builder missing variables uses default values successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .build()

        assert(KlaviyoConfig.apiKey == API_KEY)
        assert(KlaviyoConfig.networkTimeout == 500)
        assert(KlaviyoConfig.networkFlushInterval == 60000)
        assert(KlaviyoConfig.networkFlushDepth == 20)
    }

    @Test
    fun `KlaviyoConfig Builder negative variables uses default values successfully`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .networkTimeout(-5000)
            .networkFlushInterval(-5000)
            .networkFlushDepth(-10)
            .build()

        assert(KlaviyoConfig.apiKey == API_KEY)
        assert(KlaviyoConfig.networkTimeout == 500)
        assert(KlaviyoConfig.networkFlushInterval == 60000)
        assert(KlaviyoConfig.networkFlushDepth == 20)
    }

    @Test(expected = KlaviyoMissingAPIKeyException::class)
    fun `KlaviyoConfig Builder missing API key throws expected exception`() {
        KlaviyoConfig.Builder()
            .applicationContext(contextMock)
            .networkTimeout(500)
            .networkFlushInterval(60000)
            .networkFlushDepth(20)
            .build()
    }

    @Test(expected = KlaviyoMissingContextException::class)
    fun `KlaviyoConfig Builder missing application context throws exception`() {
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .networkTimeout(500)
            .networkFlushInterval(60000)
            .networkFlushDepth(20)
            .build()
    }

    @Test(expected = KlaviyoMissingPermissionException::class)
    fun `KlaviyoConfig Builder throws exception when context is missing required permissions`() {
        mockPackageInfo.requestedPermissions = arrayOf()
        KlaviyoConfig.Builder()
            .apiKey(API_KEY)
            .applicationContext(contextMock)
            .build()
    }

    @Test
    fun `getPackageInfoCompat detects platform properly`() {
        mockPackageManager.getPackageInfoCompat(contextMock.packageName, PackageManager.GET_PERMISSIONS)
        verify { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, mockPackageManagerFlags) }

        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 23)
        mockPackageManager.getPackageInfoCompat(contextMock.packageName, PackageManager.GET_PERMISSIONS)
        verify { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, any<Int>()) }
    }
}
