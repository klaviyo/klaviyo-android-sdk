package com.klaviyo.coresdk

import org.junit.Test

class KlaviyoConfigTest {
    @Test
    fun `KlaviyoConfig Builder sets variables successfully`() {
        val config = KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
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
                .build()

        assert(config.apiKey == "Fake_Key")
        assert(config.networkTimeout == 500)
        assert(config.networkFlushInterval == 60000)
    }

    @Test(expected = KlaviyoMissingAPIKeyException::class)
    fun `KlaviyoConfig Builder missing API key throws expected exception`() {
        KlaviyoConfig.Builder()
                .networkTimeout(500)
                .networkFlushInterval(60000)
                .build()
    }
}