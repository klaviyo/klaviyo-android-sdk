package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
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
    }
}
