package com.klaviyo.analytics

import android.Manifest
import android.content.pm.PackageInfo
import android.os.Build
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

internal class DevicePropertiesTest : BaseTest() {

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
}
