package com.klaviyo.coresdk.helpers

import android.content.Context
import io.mockk.clearAllMocks
import io.mockk.mockk
import org.junit.After
import org.junit.Before

abstract class BaseTest {
    companion object {
        internal const val API_KEY = "stub_public_api_key"
        internal const val EMAIL = "test@domain.com"
        internal const val PHONE = "1234567890"
        internal const val EXTERNAL_ID = "abcdefg"
        internal const val ANON_ID = "anonId123"

        internal val contextMock = mockk<Context>()
    }

    @Before
    abstract fun setup()

    @After
    fun clear() {
        clearAllMocks()
    }
}
