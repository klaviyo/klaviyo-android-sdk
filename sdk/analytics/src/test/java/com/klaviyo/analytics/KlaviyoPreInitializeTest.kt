package com.klaviyo.analytics

import android.app.Application
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateSideEffects
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class KlaviyoPreInitializeTest : BaseTest() {

    private val mockBuilder = mockk<Config.Builder>().apply {
        every { apiKey(any()) } returns this
        every { applicationContext(any()) } returns this
        every { build() } returns mockConfig
    }

    private val mockApiClient: ApiClient = mockk<ApiClient>().apply {
        every { startService() } returns Unit
        every { onApiRequest(any(), any()) } returns Unit
        every { enqueueProfile(any()) } returns Unit
        every { enqueueEvent(any(), any()) } returns Unit
        every { enqueuePushToken(any(), any()) } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()
        every { Registry.configBuilder } returns mockBuilder
        every { mockContext.applicationContext } returns mockk<Application>().apply {
            every { unregisterActivityLifecycleCallbacks(any()) } returns Unit
            every { registerActivityLifecycleCallbacks(any()) } returns Unit
        }
        Registry.register<ApiClient>(mockApiClient)
    }

    @After
    override fun cleanup() {
        Registry.get<State>().reset()
        Registry.unregister<Config>()
        Registry.unregister<State>()
        Registry.unregister<StateSideEffects>()
        Registry.unregister<ApiClient>()
        super.cleanup()
    }

    private inline fun <reified T> assertCaught() where T : Throwable {
        verify { spyLog.error(any(), any<T>()) }
    }

    @Test
    fun `Events APIs are replayed upon initializing`() {
        Klaviyo.createEvent(EventMetric.OPENED_APP)
        Klaviyo.handlePush(KlaviyoTest.mockIntent(KlaviyoTest.stubIntentExtras))
        assertCaught<MissingConfig>()

        verify(inverse = true) { mockApiClient.enqueueEvent(any(), any()) }

        Klaviyo.initialize(
            apiKey = "different-$API_KEY",
            applicationContext = mockContext
        )

        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = mockContext
        )

        verify(exactly = 1) {
            mockApiClient.enqueueEvent(
                match {
                    it.metric == EventMetric.OPENED_APP
                },
                any()
            )
        }

        verify(exactly = 1) {
            mockApiClient.enqueueEvent(
                match {
                    it.metric == EventMetric.OPENED_PUSH
                },
                any()
            )
        }
    }
}
