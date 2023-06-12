package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Before

internal open class BaseRequestTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        mockkObject(DeviceProperties)
        every { DeviceProperties.userAgent } returns "Mock User Agent"
        every { DeviceProperties.model } returns "Mock Model"
        every { DeviceProperties.applicationLabel } returns "Mock Application Label"
        every { DeviceProperties.appVersion } returns "Mock App Version"
        every { DeviceProperties.appVersionCode } returns "Mock Version Code"
        every { DeviceProperties.sdkName } returns "Mock SDK"
        every { DeviceProperties.sdkVersion } returns "Mock SDK Version"
        every { DeviceProperties.applicationId } returns "Mock App ID"
        every { DeviceProperties.platform } returns "Mock Platform"
        every { DeviceProperties.device_id } returns "Mock Device ID"
        every { DeviceProperties.manufacturer } returns "Mock Manufacturer"
        every { DeviceProperties.osVersion } returns "Mock OS Version"
        every { Klaviyo.getPushToken() } returns "Mock Push Token"
    }
}
