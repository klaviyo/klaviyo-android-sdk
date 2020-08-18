package com.klaviyo.coresdk

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import org.junit.Test

class KlaviyoConfigTest {
    private val contextMock: Context = mock()

    @Test
    fun `KlaviyoConfig Builder sets variables successfully`() {
        val config = KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .applicationContext(contextMock)
                .networkTimeout(1000)
                .networkFlushInterval(10000)
                .build()

        assert(config.apiKey == "Fake_Key")
        assert(config.networkTimeout == 1000)
        assert(config.networkFlushInterval == 10000)
    }

    @Test
    fun `KlaviyoConfig Builder missing variables uses default values successfully`() {
        val config = KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .applicationContext(contextMock)
                .build()

        assert(config.apiKey == "Fake_Key")
        assert(config.networkTimeout == 500)
        assert(config.networkFlushInterval == 60000)
    }

    @Test
    fun `KlaviyoConfig Builder negative variables uses default values successfully`() {
        val config = KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .applicationContext(contextMock)
                .networkTimeout(-5000)
                .networkFlushInterval(-5000)
                .build()

        assert(config.apiKey == "Fake_Key")
        assert(config.networkTimeout == 500)
        assert(config.networkFlushInterval == 60000)
    }

    @Test(expected = KlaviyoMissingAPIKeyException::class)
    fun `KlaviyoConfig Builder missing API key throws expected exception`() {
        KlaviyoConfig.Builder()
                .applicationContext(contextMock)
                .networkTimeout(500)
                .networkFlushInterval(60000)
                .build()
    }

    @Test(expected = KlaviyoMissingContextException::class)
    fun `KlaviyoConfig Builder missing application context throws expected exception`() {
        KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .networkTimeout(500)
                .networkFlushInterval(60000)
                .build()
    }
}