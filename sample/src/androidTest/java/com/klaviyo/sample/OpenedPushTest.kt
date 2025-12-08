package com.klaviyo.sample

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.Klaviyo.isKlaviyoNotificationIntent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Instrumented tests for push notification open tracking.
 *
 * These tests verify that the SDK correctly handles push notification intents
 * and enqueues the appropriate API requests.
 */
class OpenedPushTest : BaseInstrumentedTest() {

    @Test
    fun intentIsRecognizedAsKlaviyoNotification() {
        // Given: A Klaviyo notification intent
        val intent = createKlaviyoNotificationIntent()

        // Then: The SDK should recognize this as a Klaviyo notification
        assertTrue(
            "Intent should be recognized as Klaviyo notification",
            intent.isKlaviyoNotificationIntent
        )

        // Verify the extra is accessible
        val kExtra = intent.getStringExtra("com.klaviyo._k")
        assertNotNull("com.klaviyo._k extra should be present", kExtra)
        assertTrue("com.klaviyo._k extra should not be empty", kExtra?.isNotEmpty() == true)
    }

    @Test
    fun handlePushEnqueuesEventRequestForValidKlaviyoIntent() {
        // Given: A Klaviyo notification intent
        val intent = createKlaviyoNotificationIntent()

        // Verify the intent is recognized first
        assertTrue(
            "Intent should be recognized as Klaviyo notification",
            intent.isKlaviyoNotificationIntent
        )

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: An Event request should be enqueued
        val eventRequest = waitForRequest(EVENT_REQUEST_TYPE)
        assertNotNull("Expected an Event request to be enqueued", eventRequest)
    }

    @Test
    fun handlePushIncludesOpenedPushMetricInRequestBody() {
        // Given: A Klaviyo notification intent
        val intent = createKlaviyoNotificationIntent()

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: The request body should contain the $opened_push metric
        val eventRequest = requireNotNull(waitForRequest(EVENT_REQUEST_TYPE)) {
            "Expected an Event request to be enqueued"
        }
        val requestBody = requireNotNull(eventRequest.requestBody) {
            "Request body should not be null"
        }
        assertTrue(
            "Request body should contain \$opened_push metric",
            requestBody.contains("\$opened_push")
        )
    }

    @Test
    fun handlePushIgnoresNonKlaviyoIntents() {
        // Given: A non-Klaviyo intent (missing com.klaviyo._k)
        val intent = createNonKlaviyoIntent()

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: No Event request should be enqueued for this specific call
        // Note: We use a short timeout since we expect no request
        waitForRequest(EVENT_REQUEST_TYPE, timeoutMs = 1000L)

        // We check that we didn't add new opened_push event requests after our call
        val openedPushRequests = getCapturedRequests(EVENT_REQUEST_TYPE)
            .filter { it.requestBody?.contains("\$opened_push") == true }
        assertTrue(
            "Non-Klaviyo intent should not enqueue an opened_push Event request",
            openedPushRequests.isEmpty()
        )
    }

    @Test
    fun handlePushIgnoresNullIntents() {
        // Given: A null intent

        // When: handlePush is called with null
        Klaviyo.handlePush(null)

        // Then: No Event request should be enqueued
        waitForRequest(EVENT_REQUEST_TYPE, timeoutMs = 1000L)
        val openedPushRequests = getCapturedRequests(EVENT_REQUEST_TYPE)
            .filter { it.requestBody?.contains("\$opened_push") == true }

        assertTrue(
            "Null intent should not enqueue an opened_push Event request",
            openedPushRequests.isEmpty()
        )
    }

    @Test
    fun handlePushIncludesKlaviyoExtrasInRequest() {
        // Given: A Klaviyo notification intent with custom extras
        val customExtras = mapOf(
            "com.klaviyo._k" to """{"m": "CUSTOM_MESSAGE_ID", "c": "CUSTOM_CAMPAIGN"}""",
            "com.klaviyo.title" to "Custom Title",
            "com.klaviyo.body" to "Custom Body"
        )
        val intent = createKlaviyoNotificationIntent(customExtras)

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: The request should contain the Klaviyo extras
        val eventRequest = requireNotNull(waitForRequest(EVENT_REQUEST_TYPE)) {
            "Expected an Event request to be enqueued"
        }
        val requestBody = requireNotNull(eventRequest.requestBody) {
            "Request body should not be null"
        }

        // The extras should be included in the event properties
        // The SDK strips "com.klaviyo." prefix, so we check for the stripped keys
        assertTrue(
            "Request should contain _k tracking data",
            requestBody.contains("_k")
        )
    }

    companion object {
        /**
         * The request type string for Event API requests.
         * This matches the `type` property in [EventApiRequest].
         */
        private const val EVENT_REQUEST_TYPE = "Create Event"
    }
}
