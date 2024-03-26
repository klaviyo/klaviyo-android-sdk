package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DevicePropertiesTest
import com.klaviyo.fixtures.BaseTest
import org.junit.After
import org.junit.Before

internal open class BaseRequestTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        DevicePropertiesTest.mockDeviceProperties()
    }

    @After
    open fun cleanup() {
        DevicePropertiesTest.unmockDeviceProperties()
    }
}
