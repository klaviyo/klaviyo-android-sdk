package com.klaviyo.coresdk.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.klaviyo.coresdk.BuildConfig
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.After
import org.junit.Before

abstract class BaseTest {
    companion object {
        internal const val API_KEY = "stub_public_api_key"
        internal const val EMAIL = "test@domain.com"
        internal const val PHONE = "+12223334444"
        internal const val EXTERNAL_ID = "abcdefg"
        internal const val ANON_ID = "anonId123"

        internal val contextMock = mockk<Context>()
    }

    private val mockPackageManager = mockk<PackageManager>()
    private val mockPackageManagerFlags = mockk<PackageManager.PackageInfoFlags>()
    internal val mockPackageInfo = mockk<PackageInfo>().apply {
        requestedPermissions = arrayOf(Manifest.permission.ACCESS_NETWORK_STATE)
    }

    @Before
    open fun setup() {
        // TODO If I can isolate unit tests further, they shouldn't all need to rely on Klaviyo, KlaviyoConfig, and therefore context

        mockkStatic(PackageManager.PackageInfoFlags::class)
        every { PackageManager.PackageInfoFlags.of(any()) } returns mockPackageManagerFlags
        every { contextMock.packageManager } returns mockPackageManager
        every { contextMock.packageName } returns BuildConfig.LIBRARY_PACKAGE_NAME
        every { mockPackageManager.getPackageInfoCompat(BuildConfig.LIBRARY_PACKAGE_NAME, any()) } returns mockPackageInfo
        every { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, mockPackageManagerFlags) } returns mockPackageInfo
        every { mockPackageManager.getPackageInfo(BuildConfig.LIBRARY_PACKAGE_NAME, any<Int>()) } returns mockPackageInfo
    }

    @After
    fun clear() {
        clearAllMocks()
    }
}
