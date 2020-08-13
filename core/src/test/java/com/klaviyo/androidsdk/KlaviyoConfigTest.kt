package com.klaviyo.androidsdk

import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.KlaviyoMissingAPIKeyException
import org.junit.Test

class KlaviyoConfigTest {
    @Test
    fun `Build Configuration works properly!`() {
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
    fun `Build Configuration networking defaults work properly!`() {
        val config = KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .build()

        assert(config.apiKey == "Fake_Key")
        assert(config.networkTimeout == 500)
        assert(config.networkFlushInterval == 60000)
    }

    @Test(expected = KlaviyoMissingAPIKeyException::class)
    fun `Build Configuration throws expected exception on missing API key!`() {
        KlaviyoConfig.Builder()
                .networkTimeout(500)
                .networkFlushInterval(60000)
                .build()
    }
}