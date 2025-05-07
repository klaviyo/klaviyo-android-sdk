package com.klaviyo.core

import android.Manifest
import android.content.pm.PackageInfo
import android.os.Build
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePropertiesTest : BaseTest() {

    companion object {
        private const val MOCK_VERSION_CODE = 123
    }

    @Suppress("DEPRECATION")
    private val mockPackageInfo = mockk<PackageInfo>().apply {
        requestedPermissions = arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
        )
        packageName = "Mock Package Name"
        versionName = "Mock Version Name"
        every { longVersionCode } returns MOCK_VERSION_CODE.toLong()
        versionCode = MOCK_VERSION_CODE
    }

    @After
    override fun cleanup() {
        unmockDeviceProperties()
        super.cleanup()
    }

    @Test
    fun `getVersionCodeCompat detects platform properly`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 23)
        assertEquals(MOCK_VERSION_CODE, mockPackageInfo.getVersionCodeCompat())
        verify(exactly = 0) {
            mockPackageInfo.longVersionCode
        }

        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 28)
        mockPackageInfo.getVersionCodeCompat()
        verify {
            mockPackageInfo.longVersionCode
        }
    }

    @Test
    fun `User agent reflects SDK name override`() {
        mockDeviceProperties()
        // Use some more realistic values for this, so the expected user agent string actually matches our regexes
        every { DeviceProperties.applicationLabel } returns "MockApp"
        every { DeviceProperties.appVersion } returns "1.0.0"
        every { DeviceProperties.appVersionCode } returns "2"
        every { DeviceProperties.sdkVersion } returns "3.0.0"
        every { DeviceProperties.applicationId } returns "com.mock.app"
        every { DeviceProperties.platform } returns "Android"
        every { DeviceProperties.osVersion } returns "4"
        every { DeviceProperties.sdkName } returns "cross_platform"
        every { DeviceProperties.userAgent } answers { callOriginal() }
        every { DeviceProperties.pluginSdk } returns null
        every { DeviceProperties.pluginSdkVersion } returns null

        assertEquals(
            "MockApp/1.0.0 (com.mock.app; build:2; Android 4) klaviyo-cross-platform/3.0.0",
            DeviceProperties.userAgent
        )
    }

    @Test
    fun `User agent reflects SDK name override and plugin name`() {
        mockDeviceProperties()
        // Use some more realistic values for this, so the expected user agent string actually matches our regexes
        every { DeviceProperties.applicationLabel } returns "MockApp"
        every { DeviceProperties.appVersion } returns "1.0.0"
        every { DeviceProperties.appVersionCode } returns "2"
        every { DeviceProperties.sdkVersion } returns "3.0.0"
        every { DeviceProperties.applicationId } returns "com.mock.app"
        every { DeviceProperties.platform } returns "Android"
        every { DeviceProperties.osVersion } returns "4"
        every { DeviceProperties.sdkName } returns "cross_platform"
        every { DeviceProperties.userAgent } answers { callOriginal() }
        every { DeviceProperties.pluginSdk } returns "klaviyo-expo"
        every { DeviceProperties.pluginSdkVersion } returns "1.0.0"

        assertEquals(
            "MockApp/1.0.0 (com.mock.app; build:2; Android 4) klaviyo-cross-platform/3.0.0 (klaviyo-expo/1.0.0)",
            DeviceProperties.userAgent
        )
    }

    @Test
    fun `user agent with no plugin sdk version is not blank`() {
        mockDeviceProperties()
        // Use some more realistic values for this, so the expected user agent string actually matches our regexes
        every { DeviceProperties.applicationLabel } returns "MockApp"
        every { DeviceProperties.appVersion } returns "1.0.0"
        every { DeviceProperties.appVersionCode } returns "2"
        every { DeviceProperties.sdkVersion } returns "3.0.0"
        every { DeviceProperties.applicationId } returns "com.mock.app"
        every { DeviceProperties.platform } returns "Android"
        every { DeviceProperties.osVersion } returns "4"
        every { DeviceProperties.sdkName } returns "cross_platform"
        every { DeviceProperties.userAgent } answers { callOriginal() }
        every { DeviceProperties.pluginSdk } returns "klaviyo-expo"
        every { DeviceProperties.pluginSdkVersion } returns null

        assertEquals(
            "MockApp/1.0.0 (com.mock.app; build:2; Android 4) klaviyo-cross-platform/3.0.0 (klaviyo-expo/UNKNOWN)",
            DeviceProperties.userAgent
        )
    }
}
