package com.klaviyo.coresdk

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import org.junit.Test

class KlaviyoConfigTest {
    private val contextMock: Context = mock()

    @Test
    fun `KlaviyoConfig Builder sets variables successfully`() {
        KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .applicationContext(contextMock)
                .networkTimeout(1000)
                .networkFlushInterval(10000)
                .networkFlushDepth(10)
                .build()

        assert(KlaviyoConfig.apiKey == "Fake_Key")
        assert(KlaviyoConfig.networkTimeout == 1000)
        assert(KlaviyoConfig.networkFlushInterval == 10000)
        assert(KlaviyoConfig.networkFlushDepth == 10)
    }

    @Test
    fun `KlaviyoConfig Builder missing variables uses default values successfully`() {
        KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .applicationContext(contextMock)
                .build()

        assert(KlaviyoConfig.apiKey == "Fake_Key")
        assert(KlaviyoConfig.networkTimeout == 500)
        assert(KlaviyoConfig.networkFlushInterval == 60000)
        assert(KlaviyoConfig.networkFlushDepth == 20)
    }

    @Test
    fun `KlaviyoConfig Builder negative variables uses default values successfully`() {
        KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .applicationContext(contextMock)
                .networkTimeout(-5000)
                .networkFlushInterval(-5000)
                .networkFlushDepth(-10)
                .build()

        assert(KlaviyoConfig.apiKey == "Fake_Key")
        assert(KlaviyoConfig.networkTimeout == 500)
        assert(KlaviyoConfig.networkFlushInterval == 60000)
        assert(KlaviyoConfig.networkFlushDepth == 20)
    }

    @Test(expected = KlaviyoMissingAPIKeyException::class)
    fun `KlaviyoConfig Builder missing API key throws expected exception`() {
        KlaviyoConfig.Builder()
                .applicationContext(contextMock)
                .networkTimeout(500)
                .networkFlushInterval(60000)
                .networkFlushDepth(20)
                .build()
    }

    @Test(expected = KlaviyoMissingContextException::class)
    fun `KlaviyoConfig Builder missing application context throws expected exception`() {
        KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .networkTimeout(500)
                .networkFlushInterval(60000)
                .networkFlushDepth(20)
                .build()
    }
}