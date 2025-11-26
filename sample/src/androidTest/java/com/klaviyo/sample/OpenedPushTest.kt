package com.klaviyo.sample

import com.klaviyo.analytics.Klaviyo
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
    fun `handlePush enqueues Event request for valid Klaviyo intent`() {
        // Given: A Klaviyo notification intent
        val intent = createKlaviyoNotificationIntent()

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: An Event request should be enqueued
        val eventRequest = waitForRequest("Event")
        assertNotNull("Expected an Event request to be enqueued", eventRequest)
    }

    @Test
    fun `handlePush includes opened_push metric in request body`() {
        // Given: A Klaviyo notification intent
        val intent = createKlaviyoNotificationIntent()

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: The request body should contain the $opened_push metric
        val eventRequest = requireNotNull(waitForRequest("Event")) {
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
    fun `handlePush ignores non-Klaviyo intents`() {
        // Given: A non-Klaviyo intent (missing com.klaviyo._k)
        val intent = createNonKlaviyoIntent()

        // When: handlePush is called
        Klaviyo.handlePush(intent)

        // Then: No Event request should be enqueued for this specific call
        // Note: We use a short timeout since we expect no request
        waitForRequest("Event", timeoutMs = 1000L)

        // We check that we didn't add new opened_push event requests after our call
        val openedPushRequests = getCapturedRequests("Event")
            .filter { it.requestBody?.contains("\$opened_push") == true }
        assertTrue(
            "Non-Klaviyo intent should not enqueue an opened_push Event request",
            openedPushRequests.isEmpty()
        )
    }

    @Test
    fun `handlePush ignores null intents`() {
        // Given: A null intent

        // When: handlePush is called with null
        Klaviyo.handlePush(null)

        // Then: No Event request should be enqueued
        waitForRequest("Event", timeoutMs = 1000L)
        val openedPushRequests = getCapturedRequests("Event")
            .filter { it.requestBody?.contains("\$opened_push") == true }

        assertTrue(
            "Null intent should not enqueue an opened_push Event request",
            openedPushRequests.isEmpty()
        )
    }

    @Test
    fun `handlePush includes Klaviyo extras in request`() {
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
        val eventRequest = requireNotNull(waitForRequest("Event")) {
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
}
