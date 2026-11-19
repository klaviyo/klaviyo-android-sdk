package com.klaviyo.analytics

import android.app.Application
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.KlaviyoApiClient
import com.klaviyo.analytics.networking.QueueScheduler
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
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
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

    private fun resetInitialization() {
        listOf("initialized", "drainingPreInitQueue").forEach { name ->
            Klaviyo::class.java.getDeclaredField(name).apply {
                isAccessible = true
                setBoolean(Klaviyo, false)
            }
        }
        Klaviyo::class.java.getDeclaredField("preInitQueue").apply {
            isAccessible = true
            (get(Klaviyo) as Queue<*>).clear()
        }
    }

    private fun prepareUninitializedClient(): ProfileApiRequest {
        resetInitialization()
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
        return saved
    }

    private fun shutdownExecutor(executor: ExecutorService) {
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun cleanupClient() {
        Registry.getOrNull<StateSideEffects>()?.detach()
        spyDataStore.clear(KlaviyoApiClient.QUEUE_KEY)
        KlaviyoApiClient.restoreQueue(forceRestore = true)
        Registry.unregister<StateSideEffects>()
        unmockDeviceProperties()
        resetInitialization()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `failed tracking link before initialization preserves the saved queue`() = runTest(
        dispatcher
    ) {
        val saved = prepareUninitializedClient()
        try {
            Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)
            var result: ResolveDestinationResult? = null
            val click = Registry.get<ApiClient>().resolveDestinationUrl(
                "https://example.com/u/click",
                Profile()
            ) { result = it }
            testScheduler.runCurrent()
            assertTrue(result is ResolveDestinationResult.Unavailable)
            val indexBeforeInitialization = spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)
            every { mockBuilder.apiKey("invalid") } throws MissingAPIKey()
            Klaviyo.initialize("invalid", mockApplicationContext)
            val indexAfterFailedInitialization = spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)

            Klaviyo.initialize(API_KEY, mockApplicationContext)
            testScheduler.runCurrent()
            verify(exactly = 1) { mockNetworkMonitor.onNetworkChange(any()) }

            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(
                listOf(saved.uuid, click.uuid),
                (0 until persisted.length()).map { persisted.getString(it) }
            )
            assertEquals(JSONArray(listOf(saved.uuid)).toString(), indexBeforeInitialization)
            assertEquals(indexBeforeInitialization, indexAfterFailedInitialization)
            Klaviyo.initialize(API_KEY, mockApplicationContext)
            assertEquals(persisted.toString(), spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            verify(exactly = 1) { mockNetworkMonitor.isNetworkConnected() }
        } finally {
            cleanupClient()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `concurrent tracking retries during initialization are replayed once`() = runTest(
        dispatcher
    ) {
        val saved = prepareUninitializedClient()
        val startEntered = CountDownLatch(1)
        val continueStart = CountDownLatch(1)
        val resolutionsDone = CountDownLatch(3)
        val callbackResults = ConcurrentLinkedQueue<ResolveDestinationResult>()
        val executor = Executors.newFixedThreadPool(4)
        every { Registry.dispatcher } returns executor.asCoroutineDispatcher()
        every { mockNetworkMonitor.onNetworkChange(any()) } answers {
            startEntered.countDown()
            check(continueStart.await(10, TimeUnit.SECONDS))
        }

        try {
            Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)
            val initialization = executor.submit {
                Klaviyo.initialize(API_KEY, mockApplicationContext)
            }
            assertTrue(startEntered.await(5, TimeUnit.SECONDS))
            val client = Registry.get<ApiClient>()
            val first = client.resolveDestinationUrl("https://example.com/u/first", Profile()) {
                callbackResults.add(it)
                resolutionsDone.countDown()
            }
            val second = client.resolveDestinationUrl("https://example.com/u/second", Profile()) {
                callbackResults.add(it)
                resolutionsDone.countDown()
            }
            val third = async(Registry.dispatcher) {
                client.resolveDestinationUrl("https://example.com/u/third", Profile()).also {
                    resolutionsDone.countDown()
                }
            }
            assertTrue(resolutionsDone.await(5, TimeUnit.SECONDS))
            val indexDuringInitialization = spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)
            continueStart.countDown()
            initialization.get(5, TimeUnit.SECONDS)
            assertTrue(third.await() is ResolveDestinationResult.Unavailable)
            assertEquals(2, callbackResults.size)
            assertTrue(callbackResults.all { it is ResolveDestinationResult.Unavailable })

            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            val ids = (0 until persisted.length()).map { persisted.getString(it) }
            assertEquals(4, ids.size)
            assertEquals(saved.uuid, ids.first())
            assertTrue(ids.containsAll(listOf(first.uuid, second.uuid)))
            val urls = ids.drop(1).map { uuid ->
                JSONObject(spyDataStore.fetch(uuid)).getString("base_url")
            }.toSet()
            assertEquals(
                setOf(
                    "https://example.com/u/first",
                    "https://example.com/u/second",
                    "https://example.com/u/third"
                ),
                urls
            )
            assertEquals(JSONArray(listOf(saved.uuid)).toString(), indexDuringInitialization)
            Klaviyo.initialize(API_KEY, mockApplicationContext)
            assertEquals(persisted.toString(), spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            verify(exactly = 3) { mockNetworkMonitor.isNetworkConnected() }
        } finally {
            continueStart.countDown()
            shutdownExecutor(executor)
            every { Registry.dispatcher } returns dispatcher
            cleanupClient()
        }
    }

    @Test
    fun `tracking retry during replay is not stranded`() {
        val saved = prepareUninitializedClient()
        val firstResolved = CountDownLatch(1)
        val secondResolved = CountDownLatch(1)
        val replayStarted = CountDownLatch(1)
        val continueReplay = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        every { Registry.dispatcher } returns executor.asCoroutineDispatcher()

        try {
            Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)
            val client = Registry.get<ApiClient>()
            val first = client.resolveDestinationUrl("https://example.com/u/first", Profile()) {
                firstResolved.countDown()
            }
            assertTrue(firstResolved.await(5, TimeUnit.SECONDS))
            every { spyDataStore.store(first.uuid, any()) } answers {
                replayStarted.countDown()
                check(continueReplay.await(10, TimeUnit.SECONDS))
                callOriginal()
            }
            val initialization = executor.submit {
                Klaviyo.initialize(API_KEY, mockApplicationContext)
            }
            assertTrue(replayStarted.await(5, TimeUnit.SECONDS))
            val second = client.resolveDestinationUrl("https://example.com/u/second", Profile()) {
                secondResolved.countDown()
            }
            assertTrue(secondResolved.await(5, TimeUnit.SECONDS))
            continueReplay.countDown()
            initialization.get(5, TimeUnit.SECONDS)

            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(
                listOf(saved.uuid, first.uuid, second.uuid),
                (0 until persisted.length()).map { persisted.getString(it) }
            )
            verify(exactly = 2) { mockNetworkMonitor.isNetworkConnected() }
        } finally {
            continueReplay.countDown()
            shutdownExecutor(executor)
            every { Registry.dispatcher } returns dispatcher
            cleanupClient()
        }
    }

    @Test
    fun `push retry arriving after initialization is not stranded`() {
        val saved = prepareUninitializedClient()
        val warningStarted = CountDownLatch(1)
        val continuePush = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = mockk<QueueScheduler>(relaxed = true)
        Registry.register<QueueScheduler>(scheduler)
        every { spyLog.warning("The operation will be retried.", any()) } answers {
            warningStarted.countDown()
            check(continuePush.await(10, TimeUnit.SECONDS))
        }

        try {
            Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)
            val intent = KlaviyoPushOpenHandlerTest.mockIntent(mapOf("com.klaviyo._k" to "{}"))
            val push = executor.submit { Klaviyo.handlePush(intent) }
            assertTrue(warningStarted.await(5, TimeUnit.SECONDS))
            Klaviyo.initialize(API_KEY, mockApplicationContext)
            verify(exactly = 1) { mockNetworkMonitor.onNetworkChange(any()) }
            continuePush.countDown()
            push.get(5, TimeUnit.SECONDS)

            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(2, persisted.length())
            assertEquals(saved.uuid, persisted.getString(1))
            assertEquals(
                "EventApiRequest",
                JSONObject(spyDataStore.fetch(persisted.getString(0))).getString("request_type")
            )
            verify(exactly = 1) { scheduler.scheduleFlush() }
        } finally {
            continuePush.countDown()
            shutdownExecutor(executor)
            Registry.unregister<QueueScheduler>()
            cleanupClient()
        }
    }

    @Test
    fun `push retry added during replay is drained`() {
        val saved = prepareUninitializedClient()
        val resolved = CountDownLatch(1)
        val replayStarted = CountDownLatch(1)
        val continueReplay = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        val scheduler = mockk<QueueScheduler>(relaxed = true)
        Registry.register<QueueScheduler>(scheduler)
        every { Registry.dispatcher } returns executor.asCoroutineDispatcher()
        try {
            Klaviyo.registerForLifecycleCallbacks(mockApplicationContext)
            val click = Registry.get<ApiClient>().resolveDestinationUrl(
                "https://example.com/u/click",
                Profile()
            ) { resolved.countDown() }
            assertTrue(resolved.await(5, TimeUnit.SECONDS))
            every { spyDataStore.store(click.uuid, any()) } answers {
                replayStarted.countDown()
                check(continueReplay.await(10, TimeUnit.SECONDS))
                callOriginal()
            }
            val initialization = executor.submit {
                Klaviyo.initialize(API_KEY, mockApplicationContext)
            }
            assertTrue(replayStarted.await(5, TimeUnit.SECONDS))
            every { mockConfig.apiKey } throws MissingAPIKey()
            val intent = KlaviyoPushOpenHandlerTest.mockIntent(mapOf("com.klaviyo._k" to "{}"))
            Klaviyo.handlePush(intent)
            every { mockConfig.apiKey } returns API_KEY
            continueReplay.countDown()
            initialization.get(5, TimeUnit.SECONDS)

            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(3, persisted.length())
            assertEquals(saved.uuid, persisted.getString(1))
            assertEquals(click.uuid, persisted.getString(2))
            verify(exactly = 1) { scheduler.scheduleFlush() }
        } finally {
            continueReplay.countDown()
            shutdownExecutor(executor)
            every { Registry.dispatcher } returns dispatcher
            Registry.unregister<QueueScheduler>()
            cleanupClient()
        }
    }

    @Test
    fun `push failure after initialization waits for the next initialization`() {
        val saved = prepareUninitializedClient()
        val scheduler = mockk<QueueScheduler>(relaxed = true)
        Registry.register<QueueScheduler>(scheduler)
        try {
            Klaviyo.initialize(API_KEY, mockApplicationContext)
            every { mockConfig.apiKey } throws MissingAPIKey()
            val intent = KlaviyoPushOpenHandlerTest.mockIntent(mapOf("com.klaviyo._k" to "{}"))
            Klaviyo.handlePush(intent)
            assertEquals(
                JSONArray(listOf(saved.uuid)).toString(),
                spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY)
            )

            Klaviyo.initialize(API_KEY, mockApplicationContext)
            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(2, persisted.length())
            assertEquals(saved.uuid, persisted.getString(1))
            verify(exactly = 1) { scheduler.scheduleFlush() }
        } finally {
            Registry.unregister<QueueScheduler>()
            cleanupClient()
        }
    }

    @Test
    fun `push failure racing reinitialization is replayed`() {
        val saved = prepareUninitializedClient()
        val warningStarted = CountDownLatch(1)
        val continuePush = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = mockk<QueueScheduler>(relaxed = true)
        Registry.register<QueueScheduler>(scheduler)
        try {
            Klaviyo.initialize(API_KEY, mockApplicationContext)
            every { mockConfig.apiKey } throws MissingAPIKey()
            every { spyLog.warning("The operation will be retried.", any()) } answers {
                warningStarted.countDown()
                check(continuePush.await(10, TimeUnit.SECONDS))
            }
            val intent = KlaviyoPushOpenHandlerTest.mockIntent(mapOf("com.klaviyo._k" to "{}"))
            val push = executor.submit { Klaviyo.handlePush(intent) }
            assertTrue(warningStarted.await(5, TimeUnit.SECONDS))
            Klaviyo.initialize(API_KEY, mockApplicationContext)
            continuePush.countDown()
            push.get(5, TimeUnit.SECONDS)

            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(2, persisted.length())
            assertEquals(saved.uuid, persisted.getString(1))
            verify(exactly = 1) { scheduler.scheduleFlush() }
        } finally {
            continuePush.countDown()
            shutdownExecutor(executor)
            Registry.unregister<QueueScheduler>()
            cleanupClient()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `tracking retries after initialization enqueue immediately`() = runTest(dispatcher) {
        val saved = prepareUninitializedClient()
        try {
            Klaviyo.initialize(API_KEY, mockApplicationContext)
            var callback: ResolveDestinationResult? = null
            val client = Registry.get<ApiClient>()
            val click = client.resolveDestinationUrl("https://example.com/u/ready", Profile()) {
                callback = it
            }
            testScheduler.runCurrent()
            assertTrue(callback is ResolveDestinationResult.Unavailable)
            val persisted = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(
                listOf(saved.uuid, click.uuid),
                (0 until persisted.length()).map { persisted.getString(it) }
            )
            verify(exactly = 1) { mockNetworkMonitor.isNetworkConnected() }
        } finally {
            cleanupClient()
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
