package com.klaviyo.sample

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.Klaviyo.isKlaviyoNotificationIntent
import com.klaviyo.analytics.networking.requests.ApiRequest
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

        // Snapshot UUIDs before to verify a NEW request is created
        val uuidsBefore = getOpenedPushRequestUuids()

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: A NEW Event request should be enqueued
        val eventRequest = waitForRequest(EVENT_REQUEST_TYPE)
        assertNotNull("Expected an Event request to be enqueued", eventRequest)

        val newUuids = getOpenedPushRequestUuids() - uuidsBefore
        assertTrue(
            "handlePush should create a new opened_push request (found ${newUuids.size} new)",
            newUuids.isNotEmpty()
        )
    }

    @Test
    fun handlePushIncludesOpenedPushMetricInRequestBody() {
        // Given: A Klaviyo notification intent
        val intent = createKlaviyoNotificationIntent()

        // Snapshot UUIDs before to verify a NEW request is created
        val uuidsBefore = getOpenedPushRequestUuids()

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

        // Verify this was a NEW request
        val newUuids = getOpenedPushRequestUuids() - uuidsBefore
        assertTrue(
            "handlePush should create a new opened_push request",
            newUuids.isNotEmpty()
        )
    }

    @Test
    fun handlePushIgnoresNonKlaviyoIntents() {
        // Given: A non-Klaviyo intent (missing com.klaviyo._k)
        val intent = createNonKlaviyoIntent()

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: No Event request should be created from THIS call
        // Wait briefly and check that any captured requests have known tracking data
        // (meaning they're rebroadcasts from previous tests, not from this invalid intent)
        waitForRequest(EVENT_REQUEST_TYPE, timeoutMs = 1000L)

        val unexpectedRequests = getOpenedPushRequestsWithoutKnownTrackingData()
        assertTrue(
            "Non-Klaviyo intent should not create a request. Found ${unexpectedRequests.size} " +
                "request(s) without known tracking data: ${unexpectedRequests.mapNotNull { it.uuid }}",
            unexpectedRequests.isEmpty()
        )
    }

    @Test
    fun handlePushIgnoresNullIntents() {
        // When: handlePush is called with null
        Klaviyo.handlePush(null)

        // Then: No Event request should be created from THIS call
        // Wait briefly and check that any captured requests have known tracking data
        // (meaning they're rebroadcasts from previous tests, not from this null intent)
        waitForRequest(EVENT_REQUEST_TYPE, timeoutMs = 1000L)

        val unexpectedRequests = getOpenedPushRequestsWithoutKnownTrackingData()
        assertTrue(
            "Null intent should not create a request. Found ${unexpectedRequests.size} " +
                "request(s) without known tracking data: ${unexpectedRequests.mapNotNull { it.uuid }}",
            unexpectedRequests.isEmpty()
        )
    }

    /**
     * Gets opened_push requests that do NOT contain known tracking data from test intents.
     * Requests with known tracking data (TEST_MESSAGE_ID, CUSTOM_MESSAGE_ID) came from
     * previous positive tests and can be ignored. Requests WITHOUT this data would indicate
     * a request was created from a null/invalid intent (which would be a bug).
     */
    private fun getOpenedPushRequestsWithoutKnownTrackingData(): List<ApiRequest> =
        getCapturedRequests(EVENT_REQUEST_TYPE)
            .filter { it.requestBody?.contains("\$opened_push") == true }
            .filter { request ->
                val body = request.requestBody ?: return@filter true
                // If request has known tracking data, it's from a previous positive test
                val hasKnownTrackingData = body.contains("TEST_MESSAGE_ID") ||
                    body.contains("CUSTOM_MESSAGE_ID")
                !hasKnownTrackingData
            }

    /**
     * Gets the UUIDs of all opened_push Event requests currently captured.
     * Used to verify positive tests created new requests.
     */
    private fun getOpenedPushRequestUuids(): Set<String> =
        getCapturedRequests(EVENT_REQUEST_TYPE)
            .filter { it.requestBody?.contains("\$opened_push") == true }
            .mapNotNull { it.uuid }
            .toSet()

    @Test
    fun handlePushIncludesKlaviyoExtrasInRequest() {
        // Given: A Klaviyo notification intent with custom extras
        val customExtras = mapOf(
            "com.klaviyo._k" to """{"m": "CUSTOM_MESSAGE_ID", "c": "CUSTOM_CAMPAIGN"}""",
            "com.klaviyo.title" to "Custom Title",
            "com.klaviyo.body" to "Custom Body"
        )
        val intent = createKlaviyoNotificationIntent(customExtras)

        // Snapshot UUIDs before to verify a NEW request is created
        val uuidsBefore = getOpenedPushRequestUuids()

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

        // Verify this was a NEW request
        val newUuids = getOpenedPushRequestUuids() - uuidsBefore
        assertTrue(
            "handlePush should create a new opened_push request",
            newUuids.isNotEmpty()
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
