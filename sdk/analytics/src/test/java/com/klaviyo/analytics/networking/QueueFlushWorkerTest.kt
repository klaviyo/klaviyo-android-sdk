package com.klaviyo.analytics.networking

import android.Manifest
import android.app.Application
import android.content.pm.PackageInfo
import android.content.res.Resources
import android.os.Build
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.HttpUtil
import com.klaviyo.analytics.networking.requests.buildEventMetaData
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateSideEffects
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.util.Queue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class QueueFlushWorkerTest : BaseTest() {

    private val mockApiClient = mockk<ApiClient>(relaxed = true).apply {
        every { restoreQueue() } returns Unit
        coEvery { awaitFlushQueueOutcome() } returns FlushOutcome.Complete
    }
    private val workerParams = mockk<WorkerParameters>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()

        // Mock Klaviyo to prevent initialization attempts
        // mockkStatic is required for @JvmStatic methods
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        every { Klaviyo.registerForLifecycleCallbacks(any()) } answers {
            Registry.register<ApiClient>(mockApiClient)
            Klaviyo
        }
    }

    @After
    override fun cleanup() {
        unmockkObject(Klaviyo)
        unmockkStatic(Klaviyo::class)
        Registry.unregister<ApiClient>()
        super.cleanup()
    }

    // Build worker and run the job
    private suspend fun doWork() = QueueFlushWorker(mockContext, workerParams).doWork()

    @Test
    fun `Worker flushes queue successfully from uninitialized state`() = runTest {
        val result = doWork()

        // Verify success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify queue was flushed
        verify(exactly = 1) { Klaviyo.registerForLifecycleCallbacks(mockContext) }
        verify(exactly = 1) { mockApiClient.restoreQueue() }
        coVerify(exactly = 1) { mockApiClient.awaitFlushQueueOutcome() }
    }

    @Test
    fun `Worker flushes queue successfully from pre-initialized state`() = runTest {
        // Pre-register dependencies
        Registry.register<Config>(mockConfig)
        Registry.register<ApiClient>(mockApiClient)

        // Execute the work
        val result = doWork()

        // Verify success
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify queue was flushed, and didn't need to configure dependencies
        verify(inverse = true) { Klaviyo.registerForLifecycleCallbacks(mockContext) }
        verify(exactly = 1) { mockApiClient.restoreQueue() }
        coVerify(exactly = 1) { mockApiClient.awaitFlushQueueOutcome() }
    }

    @Test
    fun `Worker returns success even when exception occurs`() = runTest {
        // Setup ApiClient to throw an exception
        every { mockApiClient.restoreQueue() } throws RuntimeException("Test exception")

        val result = doWork()

        // Should still return success to prevent WorkManager retries, but flush wasn't called
        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { mockApiClient.restoreQueue() }
        coVerify(inverse = true) { mockApiClient.awaitFlushQueueOutcome() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class QueueFlushWorkerRestartTest : BaseTest() {
    @Test
    fun `worker flushes a persisted priority event after process restart`() = runTest {
        val queueInitialized = KlaviyoApiClient::class.java.getDeclaredField("queueInitialized")
            .apply { isAccessible = true }
        val queue = KlaviyoApiClient::class.java.getDeclaredField("apiQueue")
            .apply { isAccessible = true }
            .get(KlaviyoApiClient) as Queue<*>
        val host = mockk<Application>(relaxed = true)
        val resources = mockk<Resources>()
        val packageInfo = mockk<PackageInfo>().apply {
            requestedPermissions = arrayOf(
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CHANGE_NETWORK_STATE
            )
        }
        val connection = mockk<HttpURLConnection>(relaxed = true)
        var workerError: Throwable? = null

        mockDeviceProperties()
        mockkStatic(DeviceProperties::buildEventMetaData)
        mockkObject(HttpUtil)
        try {
            queue.clear()
            queueInitialized.setBoolean(KlaviyoApiClient, false)
            setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 32)
            every { Registry.config } answers { Registry.get<Config>() }
            every { Registry.dispatcher } returns StandardTestDispatcher(testScheduler)
            every { mockContext.applicationContext } returns host
            every { mockContext.resources } returns resources
            every { resources.getString(any()) } returns "test"
            every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
            mockApplicationInfo.metaData = mockk(relaxed = true)
            every { mockPackageManager.getApplicationInfo(any<String>(), any<Int>()) } returns mockApplicationInfo
            every { mockHandler.post(any()) } returns true
            every { mockNetworkMonitor.isNetworkConnected() } returns true
            every { mockNetworkMonitor.getNetworkType() } returns NetworkMonitor.NetworkType.Wifi
            every { mockNetworkMonitor.onNetworkChange(any()) } returns Unit
            every { mockNetworkMonitor.offNetworkChange(any()) } returns Unit
            every { DeviceProperties.buildEventMetaData() } returns emptyMap()
            every { HttpUtil.openConnection(any()) } returns connection
            every { HttpUtil.writeToConnection(any(), any()) } returns Unit
            every { connection.responseCode } returns 202
            every { connection.headerFields } returns emptyMap()
            every { connection.inputStream } returns ByteArrayInputStream(ByteArray(0))
            Registry.register<QueueScheduler>(mockk<QueueScheduler>(relaxed = true))

            Klaviyo.initialize(API_KEY, mockContext)
            assertEquals(API_KEY, Registry.config.apiKey)
            assertEquals(API_KEY, spyDataStore.fetch("api_key"))
            val request = KlaviyoApiClient.enqueueEvent(
                Event(EventMetric.OPENED_PUSH),
                Profile(email = EMAIL)
            )
            val persistedQueue = JSONArray(spyDataStore.fetch(KlaviyoApiClient.QUEUE_KEY))
            assertEquals(request.uuid, persistedQueue.getString(0))
            val persistedRequest = JSONObject(spyDataStore.fetch(request.uuid))
            assertEquals("EventApiRequest", persistedRequest.getString("request_type"))
            assertEquals(API_KEY, persistedRequest.getJSONObject("query").getString("company_id"))

            Registry.get<StateSideEffects>().detach()
            Registry.unregister<StateSideEffects>()
            Registry.unregister<State>()
            Registry.unregister<ApiClient>()
            Registry.unregister<Config>()
            Registry.unregister<QueueScheduler>()
            queue.clear()
            queueInitialized.setBoolean(KlaviyoApiClient, false)
            assertFalse(Registry.isRegistered<Config>())
            every { spyLog.error(any(), any()) } answers { workerError = secondArg() }

            val result = QueueFlushWorker(mockContext, mockk<WorkerParameters>(relaxed = true)).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertNull(
                "Worker failed to restore and flush the persisted event: " +
                    workerError?.stackTraceToString(),
                workerError
            )
            assertNull(
                "Flushed event must be removed from storage",
                spyDataStore.fetch(request.uuid)
            )
        } finally {
            Registry.getOrNull<StateSideEffects>()?.detach()
            Registry.unregister<StateSideEffects>()
            Registry.unregister<State>()
            Registry.unregister<ApiClient>()
            Registry.unregister<Config>()
            Registry.unregister<QueueScheduler>()
            queue.clear()
            queueInitialized.setBoolean(KlaviyoApiClient, false)
            setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 33)
            unmockkObject(HttpUtil)
            unmockkStatic(DeviceProperties::buildEventMetaData)
            unmockDeviceProperties()
        }
    }
}
