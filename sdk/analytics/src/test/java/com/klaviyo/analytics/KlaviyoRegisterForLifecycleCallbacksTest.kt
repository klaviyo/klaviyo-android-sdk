package com.klaviyo.analytics

import android.app.Application
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyAll
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class KlaviyoRegisterForLifecycleCallbacksTest : BaseTest() {

    private val mockBuilder = mockk<Config.Builder>().apply {
        every { apiKey(any()) } returns this
        every { applicationContext(any()) } returns this
        every { build() } returns mockConfig
    }

    private val mockApplicationContext = mockk<Application> {
        every { applicationContext } returns this
        every { unregisterActivityLifecycleCallbacks(any()) } returns Unit
        every { unregisterComponentCallbacks(any()) } returns Unit
        every { registerActivityLifecycleCallbacks(any()) } returns Unit
        every { registerComponentCallbacks(any()) } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()
        every { Registry.configBuilder } returns mockBuilder
        every { mockContext.applicationContext } returns mockApplicationContext
    }

    @After
    override fun cleanup() {
        super.cleanup()
        Registry.unregister<Config>()
        Registry.unregister<State>()
        Registry.unregister<ApiClient>()
        unmockkAll()
    }

    @Test
    fun `check that lifecycle register function does not touch api key dependent functions`() {
        val expectedListener = Registry.lifecycleCallbacks
        val expectedConfigListener = Registry.componentCallbacks

        Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)

        verifyAll {
            mockApplicationContext.unregisterActivityLifecycleCallbacks(
                match { it == expectedListener }
            )
            mockApplicationContext.registerActivityLifecycleCallbacks(
                match { it == expectedListener }
            )
            mockApplicationContext.unregisterComponentCallbacks(
                match { it == expectedConfigListener }
            )
            mockApplicationContext.registerComponentCallbacks(
                match { it == expectedConfigListener }
            )
        }

        verify(exactly = 0) {
            mockBuilder.apiKey(any())
        }
    }
}
