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
        var configBuilt = false

        every { apiKey(any()) } returns this
        every { applicationContext(any()) } returns this
        every { build() } answers {
            configBuilt = true
            mockConfig
        }

        // Mock real world behavior where accessing data store prior to initializing throws MissingConfig
        // While also allowing spyDataStore to work post-initialization
        every { spyDataStore.fetch(any()) } answers {
            if (!configBuilt) {
                throw MissingConfig()
            } else {
                "value"
            }
        }
    }

    private val mockApiClient: ApiClient = mockk<ApiClient>().apply {
        every { startService() } returns Unit
        every { onApiRequest(any(), any()) } returns Unit
        every { offApiRequest(any()) } returns Unit
        every { enqueueProfile(any()) } returns Unit
        every { enqueueEvent(any(), any()) } returns Unit
        every { enqueuePushToken(any(), any()) } returns Unit
        every { enqueueUnregisterPushToken(any(), any(), any()) } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()
        every { Registry.configBuilder } returns mockBuilder
        every { mockContext.applicationContext } returns mockk<Application>().apply {
            every { unregisterActivityLifecycleCallbacks(any()) } returns Unit
            every { unregisterComponentCallbacks(any()) } returns Unit
            every { registerActivityLifecycleCallbacks(any()) } returns Unit
            every { registerComponentCallbacks(any()) } returns Unit
        }
        Registry.register<ApiClient>(mockApiClient)
    }

    @After
    override fun cleanup() {
        Registry.unregister<Config>()
        Registry.get<State>().reset()
        Registry.unregister<State>()
        Registry.unregister<StateSideEffects>()
        Registry.unregister<ApiClient>()
        super.cleanup()
    }

    private inline fun <reified T> assertCaught() where T : Throwable {
        verify { spyLog.error(any(), any<T>()) }
    }

    @Test
    fun `Opened Push events are replayed upon initializing`() {
        Klaviyo.handlePush(KlaviyoTest.mockIntent(KlaviyoTest.stubIntentExtras))
        assertCaught<MissingConfig>()

        Klaviyo.createEvent(EventMetric.OPENED_APP)
        assertCaught<MissingConfig>()

        verify(inverse = true) { mockApiClient.enqueueEvent(any(), any()) }

        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = mockContext
        )

        Klaviyo.initialize(
            apiKey = "different-$API_KEY",
            applicationContext = mockContext
        )

        verify(inverse = true) {
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
