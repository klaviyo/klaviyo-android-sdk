package com.klaviyo.analytics

import android.app.Application
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.buildEventMetaData
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateSideEffects
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.config.Config
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
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
                PERSISTED_VALUE
            }
        }
    }

    private val mockAuthTokenManager = mockk<AuthTokenManager>(relaxed = true)

    private val mockApiClient: ApiClient = mockk<ApiClient>().apply {
        every { startService() } returns Unit
        every { onApiRequest(any(), any()) } returns Unit
        every { offApiRequest(any()) } returns Unit
        every { enqueueProfile(any()) } returns mockk(relaxed = true)
        every { enqueueEvent(any(), any()) } returns mockk(relaxed = true)
        every { enqueuePushToken(any(), any()) } returns mockk(relaxed = true)
        every { enqueueUnregisterPushToken(any(), any(), any()) } returns mockk(relaxed = true)
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
        Registry.register<AuthTokenManager>(mockAuthTokenManager)
        mockkStatic(DeviceProperties::buildEventMetaData)
        every { DeviceProperties.buildEventMetaData() } returns emptyMap()
    }

    @After
    override fun cleanup() {
        unmockkStatic(DeviceProperties::buildEventMetaData)
        Registry.unregister<Config>()
        Registry.get<State>().reset()
        Registry.unregister<State>()
        Registry.unregister<StateSideEffects>()
        Registry.unregister<ApiClient>()
        Registry.unregister<AuthTokenManager>()
        super.cleanup()
    }

    @Test
    fun `Profile changes before initialize are dropped and the token gate opens from persisted state`() {
        Klaviyo.registerAuthTokenProvider(mockk(relaxed = true))
        Klaviyo.setEmail(EMAIL)

        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = mockContext
        )

        assertEquals(PERSISTED_VALUE, Klaviyo.getEmail())
        verifyOrder {
            mockAuthTokenManager.registerProvider(any())
            mockAuthTokenManager.setIdentified(true)
        }
        verify(exactly = 1) { mockAuthTokenManager.setIdentified(any()) }
        // The only fence is the company change from the persisted API key to API_KEY
        verify(exactly = 1) { mockAuthTokenManager.invalidate() }
    }

    @Test
    fun `Opened Push events are replayed upon initializing`() {
        Klaviyo.handlePush(
            KlaviyoPushOpenHandlerTest.mockIntent(KlaviyoPushOpenHandlerTest.stubIntentExtras)
        )
        verify { spyLog.warning(any(), any<MissingConfig>()) } // Warning bc it will be replayed

        Klaviyo.createEvent(EventMetric.OPENED_APP)
        verify { spyLog.error(any(), any<MissingConfig>()) } // Error bc this is a fully invalid call

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

    private companion object {
        const val PERSISTED_VALUE = "value"
    }
}
