package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.DeviceProperties
import com.klaviyo.fixtures.BaseTest
import io.mockk.mockk

internal open class BaseRequestTest : BaseTest() {

    protected val devicePropertiesMock = mockk<DeviceProperties>()

    override fun setup() {
        super.setup()
    }
}
