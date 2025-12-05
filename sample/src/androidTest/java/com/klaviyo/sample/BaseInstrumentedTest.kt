package com.klaviyo.sample

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.ApiObserver
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Base class for instrumented tests providing common setup, teardown,
 * and helper methods for testing SDK behavior.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseInstrumentedTest {

    companion object {
        /**
         * Klaviyo notification extras - matches the structure expected by SDK.
         * The `com.klaviyo._k` key is what identifies an intent as a Klaviyo notification.
         */
        val STUB_KLAVIYO_EXTRAS = mapOf(
            "com.klaviyo.body" to "Test notification body",
            "com.klaviyo._k" to """{
                "Push Platform": "android",
                "${'$'}flow": "",
                "${'$'}message": "TEST_MESSAGE_ID",
                "${'$'}variation": "",
                "Message Name": "test_push",
                "Message Type": "campaign",
                "c": "TEST_CAMPAIGN",
                "m": "TEST_MESSAGE_ID",
                "t": "1234567890",
                "timestamp": "2024-01-01T00:00:00+0000",
                "x": "manual"
            }"""
        )

        private const val DEFAULT_TIMEOUT_MS = 5000L
    }

    /**
     * Thread-safe list to capture API requests for verification
     */
    protected val capturedRequests = CopyOnWriteArrayList<ApiRequest>()

    /**
     * Observer registered with ApiClient to capture requests
     */
    private var apiObserver: ApiObserver? = null

    /**
     * Application context for tests
     */
    protected val appContext by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Before
    open fun setup() {
        // Clear any previously captured requests
        capturedRequests.clear()

        // Ensure SDK is fully initialized - SampleApplication may not have run yet in test context
        // Check for State registration which only happens during full initialization (not just lifecycle registration)
        if (!Registry.isRegistered<State>()) {
            Klaviyo.initialize(BuildConfig.KLAVIYO_PUBLIC_KEY, appContext)
        }

        // Register observer to capture API requests
        apiObserver = { request: ApiRequest ->
            capturedRequests.add(request)
        }
        Registry.get<ApiClient>().onApiRequest(observer = apiObserver!!)
    }

    @After
    open fun teardown() {
        // Unregister the API observer
        apiObserver?.let { observer ->
            Registry.get<ApiClient>().offApiRequest(observer)
        }
        apiObserver = null
        capturedRequests.clear()
    }

    /**
     * Creates a real Intent with Klaviyo notification extras.
     * This simulates what the app receives when a push notification is opened.
     *
     * @param extras Map of string extras to add to the intent. Defaults to [STUB_KLAVIYO_EXTRAS].
     * @return Intent configured as a Klaviyo notification intent
     */
    protected fun createKlaviyoNotificationIntent(
        extras: Map<String, String> = STUB_KLAVIYO_EXTRAS
    ): Intent = Intent().apply {
        extras.forEach { (key, value) ->
            putExtra(key, value)
        }
    }

    /**
     * Creates an Intent that is NOT a Klaviyo notification (missing _k extra).
     * Useful for testing that non-Klaviyo intents are ignored.
     */
    protected fun createNonKlaviyoIntent(): Intent = Intent().apply {
        putExtra("some.other.package.key", "value")
    }

    /**
     * Waits for a request of the specified type to be captured.
     *
     * @param requestType The type string to match (e.g., "Event", "Profile", "PushToken")
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return The first matching request, or null if timeout reached
     */
    protected fun waitForRequest(
        requestType: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): ApiRequest? {
        val latch = CountDownLatch(1)
        var matchingRequest: ApiRequest? = null

        // Set up observer first to avoid race condition where request arrives
        // between checking capturedRequests and registering the observer
        val waitObserver: ApiObserver = { request ->
            if (request.type == requestType && matchingRequest == null) {
                matchingRequest = request
                latch.countDown()
            }
        }

        Registry.get<ApiClient>().onApiRequest(observer = waitObserver)

        try {
            // Check if already captured (after registering observer to avoid race)
            capturedRequests.find { it.type == requestType }?.let {
                return it
            }

            // Wait for the request or timeout
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            Registry.get<ApiClient>().offApiRequest(waitObserver)
        }

        return matchingRequest ?: capturedRequests.find { it.type == requestType }
    }

    /**
     * Returns all captured requests of a specific type
     */
    protected fun getCapturedRequests(requestType: String): List<ApiRequest> =
        capturedRequests.filter { it.type == requestType }
}
