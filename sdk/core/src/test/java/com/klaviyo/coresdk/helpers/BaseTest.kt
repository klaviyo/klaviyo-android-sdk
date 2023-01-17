package com.klaviyo.coresdk.helpers

import android.content.Context
import io.mockk.clearAllMocks
import io.mockk.mockk
import org.junit.After
import org.junit.Before

abstract class BaseTest {
    companion object {
        internal const val API_KEY = "stub_public_api_key"
        internal val contextMock = mockk<Context>()
    }

    @Before
    abstract fun setup()

    @After
    fun clear() {
        clearAllMocks()
    }
}
