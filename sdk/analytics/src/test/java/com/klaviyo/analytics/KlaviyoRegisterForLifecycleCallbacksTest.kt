package com.klaviyo.analytics

import android.app.Application
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.KlaviyoApiClient
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.ProfileApiRequest
import com.klaviyo.analytics.networking.requests.ResolveDestinationResult
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateSideEffects
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.MissingAPIKey
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `failed tracking link before initialization preserves the saved queue`() = runTest(
        dispatcher
    ) {
        Registry.unregister<Config>()
        Registry.unregister<ApiClient>()
        Registry.unregister<State>()
        Registry.unregister<StateSideEffects>()
        mockDeviceProperties()
        every { mockNetworkMonitor.isNetworkConnected() } returns false
        every { mockNetworkMonitor.getNetworkType() } returns NetworkMonitor.NetworkType.Wifi
        every { mockNetworkMonitor.onNetworkChange(any()) } returns Unit
        every { mockNetworkMonitor.offNetworkChange(any()) } returns Unit
        every { mockLifecycleMonitor.onActivityEvent(any()) } returns Unit
        every { mockLifecycleMonitor.offActivityEvent(any()) } returns Unit
        every { mockHandler.postDelayed(any(), any()) } returns true

        val clientClass = KlaviyoApiClient::class.java
        clientClass.getDeclaredField("apiQueue").apply {
            isAccessible = true
            set(KlaviyoApiClient, ConcurrentLinkedDeque<KlaviyoApiRequest>())
        }
        clientClass.getDeclaredField("queueInitialized").apply {
            isAccessible = true
            setBoolean(KlaviyoApiClient, false)
        }

        val saved = ProfileApiRequest(Profile().setAnonymousId(ANON_ID))
        spyDataStore.store(saved.uuid, saved.toString())
        spyDataStore.store(KlaviyoApiClient.QUEUE_KEY, JSONArray(listOf(saved.uuid)).toString())
        every { mockConfig.apiKey } throws MissingAPIKey()
        every { mockBuilder.apiKey(API_KEY) } answers {
            every { mockConfig.apiKey } returns API_KEY
            mockBuilder
        }

        try {
            Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)
            var result: ResolveDestinationResult? = null
            val click = Registry.get<ApiClient>().resolveDestinationUrl(
                "https://example.com/u/click",
                Profile()
            ) { result = it }
            testScheduler.runCurrent()
            assertTrue(result is ResolveDestinationResult.Unavailable)
            assertEquals(
                JSONArray(listOf(saved.uuid, click.uuid)).toString(),
                spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)
            )
            assertTrue(spyDataStore.fetch(click.uuid) != null)

            Klaviyo.initialize(API_KEY, mockApplicationContext)
            testScheduler.runCurrent()
            verify(exactly = 1) { mockNetworkMonitor.onNetworkChange(any()) }

            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(
                listOf(saved.uuid, click.uuid),
                (0 until persisted.length()).map { persisted.getString(it) }
            )
        } finally {
            Registry.getOrNull<StateSideEffects>()?.detach()
            spyDataStore.clear(KlaviyoApiClient.QUEUE_KEY)
            KlaviyoApiClient.restoreQueue(forceRestore = true)
            Registry.unregister<StateSideEffects>()
            unmockDeviceProperties()
        }
    }

    @Test
    fun `check that lifecycle register function does not touch api key dependent functions`() {
        val expectedListener = Registry.lifecycleCallbacks
        val expectedConfigListener = Registry.componentCallbacks

        Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)

        verify { mockApplicationContext.unregisterActivityLifecycleCallbacks(expectedListener) }
        verify { mockApplicationContext.registerActivityLifecycleCallbacks(expectedListener) }
        verify { mockApplicationContext.unregisterComponentCallbacks(expectedConfigListener) }
        verify { mockApplicationContext.registerComponentCallbacks(expectedConfigListener) }

        verify(exactly = 0) {
            mockBuilder.apiKey(any())
        }
    }
}
