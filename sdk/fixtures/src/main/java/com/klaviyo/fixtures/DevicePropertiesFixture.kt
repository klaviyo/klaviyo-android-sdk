package com.klaviyo.fixtures

import com.klaviyo.core.DeviceProperties
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject

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
    every { DeviceProperties.pluginSdk } returns "klaviyo-mock-plugin"
    every { DeviceProperties.pluginSdkVersion } returns "1.0.0"
}

fun unmockDeviceProperties() {
    unmockkObject(DeviceProperties)
}
