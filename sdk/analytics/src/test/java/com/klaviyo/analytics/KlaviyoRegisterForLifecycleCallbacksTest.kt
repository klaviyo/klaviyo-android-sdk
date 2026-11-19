package com.klaviyo.analytics

import android.app.Application
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.KlaviyoApiClient
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.ProfileApiRequest
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
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun setField(target: Any, name: String, value: Any) {
        target::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private fun resetKlaviyo() {
        setField(Klaviyo, "initialized", false)
        Klaviyo::class.java.getDeclaredField("preInitQueue").apply {
            isAccessible = true
            (get(Klaviyo) as Queue<*>).clear()
        }
    }

    private fun persistedQueue() = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)).let {
        (0 until it.length()).map { i -> it.getString(i) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun withSavedQueue(block: TestScope.(ProfileApiRequest) -> Unit) = runTest(dispatcher) {
        resetKlaviyo()
        Registry.unregister<Config>()
        Registry.unregister<State>()
        Registry.unregister<ApiClient>()
        Registry.unregister<StateSideEffects>()
        mockDeviceProperties()
        every { mockNetworkMonitor.isNetworkConnected() } returns false
        every { mockNetworkMonitor.getNetworkType() } returns NetworkMonitor.NetworkType.Wifi
        every { mockNetworkMonitor.onNetworkChange(any()) } returns Unit
        every { mockNetworkMonitor.offNetworkChange(any()) } returns Unit
        every { mockLifecycleMonitor.onActivityEvent(any()) } returns Unit
        every { mockLifecycleMonitor.offActivityEvent(any()) } returns Unit
        every { mockHandler.postDelayed(any(), any()) } returns true
        setField(KlaviyoApiClient, "apiQueue", ConcurrentLinkedDeque<KlaviyoApiRequest>())
        setField(KlaviyoApiClient, "queueInitialized", false)
        val saved = ProfileApiRequest(Profile().setAnonymousId(ANON_ID))
        spyDataStore.store(saved.uuid, saved.toString())
        spyDataStore.store(KlaviyoApiClient.QUEUE_KEY, JSONArray(listOf(saved.uuid)).toString())
        every { mockConfig.apiKey } throws MissingAPIKey()
        every { mockBuilder.apiKey(API_KEY) } answers {
            every { mockConfig.apiKey } returns API_KEY
            mockBuilder
        }
        try {
            block(saved)
        } finally {
            Registry.getOrNull<StateSideEffects>()?.detach()
            spyDataStore.clear(KlaviyoApiClient.QUEUE_KEY)
            KlaviyoApiClient.restoreQueue(forceRestore = true)
            Registry.unregister<StateSideEffects>()
            unmockDeviceProperties()
            resetKlaviyo()
        }
    }

    private fun TestScope.resolveFailedClick(): ApiRequest {
        Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)
        return Registry.get<ApiClient>().resolveDestinationUrl(
            "https://example.com/u/click",
            Profile()
        ) { }.also { testScheduler.runCurrent() }
    }

    @Test
    fun `failed click before initialize is enqueued after the saved queue`() = withSavedQueue { saved ->
        val click = resolveFailedClick()

        Klaviyo.initialize(API_KEY, mockApplicationContext)

        assertEquals(listOf(saved.uuid, click.uuid), persistedQueue())
    }

    @Test
    fun `failed click before initialize does not overwrite the saved queue`() = withSavedQueue { saved ->
        val click = resolveFailedClick()

        assertEquals(listOf(saved.uuid), persistedQueue())
        assertNull(spyDataStore.fetch(click.uuid))
    }

    @Test
    fun `failed click after initialize is enqueued immediately`() = withSavedQueue { saved ->
        Klaviyo.initialize(API_KEY, mockApplicationContext)

        val click = resolveFailedClick()

        assertEquals(listOf(saved.uuid, click.uuid), persistedQueue())
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
