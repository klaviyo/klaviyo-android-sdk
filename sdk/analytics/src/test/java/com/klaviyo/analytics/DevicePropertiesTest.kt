package com.klaviyo.analytics

import android.Manifest
import android.content.pm.PackageInfo
import android.os.Build
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

internal class DevicePropertiesTest : BaseTest() {

    companion object {
        private val mockVersionCode = 123

        fun mockDeviceProperties() {
            mockkObject(DeviceProperties)
            every { DeviceProperties.userAgent } returns "Mock User Agent"
            every { DeviceProperties.model } returns "Mock Model"
            every { DeviceProperties.applicationLabel } returns "Mock Application Label"
            every { DeviceProperties.appVersion } returns "Mock App Version"
            every { DeviceProperties.appVersionCode } returns "Mock Version Code"
            every { DeviceProperties.sdkName } returns "Mock SDK"
            every { DeviceProperties.sdkVersion } returns "Mock SDK Version"
            every { DeviceProperties.backgroundDataEnabled } returns true
            every { DeviceProperties.notificationPermissionGranted } returns true
            every { DeviceProperties.applicationId } returns "Mock App ID"
            every { DeviceProperties.platform } returns "Android"
            every { DeviceProperties.deviceId } returns "Mock Device ID"
            every { DeviceProperties.manufacturer } returns "Mock Manufacturer"
            every { DeviceProperties.osVersion } returns "Mock OS Version"
        }

        fun unmockDeviceProperties() {
            unmockkObject(DeviceProperties)
        }
    }

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

    @After
    override fun cleanup() {
        unmockDeviceProperties()
        super.cleanup()
    }

    @Test
    fun `getVersionCodeCompat detects platform properly`() {
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 23)
        assertEquals(mockVersionCode, mockPackageInfo.getVersionCodeCompat())
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

        assertEquals(
            "MockApp/1.0.0 (com.mock.app; build:2; Android 4) klaviyo-cross-platform/3.0.0",
            DeviceProperties.userAgent
        )
    }
}
