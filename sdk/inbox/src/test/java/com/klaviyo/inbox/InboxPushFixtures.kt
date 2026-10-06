package com.klaviyo.inbox

import android.net.Uri
import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.json.JSONArray
import org.json.JSONObject

/**
 * Klaviyo push payloads shaped like the FCM `data` map the push builder sends
 */
internal object InboxPushFixtures {
    const val TRANSMISSION_ID = "tm-123"
    const val FCM_MESSAGE_ID = "0:1791313200000%abc"

    /** `2026-10-06T19:00:00.123456+00:00` */
    const val SENT_AT_MILLIS = 1_791_313_200_123L

    fun tracking(vararg overrides: Pair<String, Any?>): String = JSONObject(
        mapOf(
            "tm" to TRANSMISSION_ID,
            "\$message" to "message-1",
            "timestamp" to "2026-10-06T19:00:00.123456+00:00",
            "Message Type" to "campaign",
            "\$campaign" to "campaign-1",
            "\$variation" to "variation-1",
            "\$message_variation" to "message-variation-1",
            "\$campaign_audience" to "audience-1",
            "pt" to "device-push-token"
        ) + overrides
    ).toString()

    fun actionButtons(vararg buttons: Map<String, String>): String = JSONArray(
        buttons.map { JSONObject(it) }
    ).toString()

    fun canonicalPayload(): MutableMap<String, String> = mutableMapOf(
        "_k" to tracking(),
        "title" to "Sale today",
        "body" to "Everything is 20% off",
        "url" to "myapp://sale",
        "image_url" to "https://cdn.example.com/sale.png",
        "image_type" to "png",
        "key_value_pairs" to JSONObject(mapOf("promo" to "SALE20")).toString(),
        "action_buttons" to actionButtons(
            mapOf(
                "id" to "shop",
                "label" to "Shop",
                "action" to "deep_link",
                "url" to "myapp://shop"
            ),
            mapOf(
                "id" to "site",
                "label" to "Website",
                "action" to "open_url",
                "url" to "https://example.com"
            ),
            mapOf("id" to "open", "label" to "Open", "action" to "open_app")
        ),
        "notification_tag" to "sale-tag",
        "sound" to "default",
        "notification_count" to "3"
    )

    fun remoteMessage(
        data: Map<String, String> = canonicalPayload(),
        messageId: String? = FCM_MESSAGE_ID
    ): RemoteMessage = mockk<RemoteMessage>().apply {
        every { this@apply.data } returns data
        every { this@apply.messageId } returns messageId
    }

    /**
     * Stands in for [Uri.parse], which is an Android stub in unit tests
     */
    fun mockUriParsing() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val value = firstArg<String>()
            mockk<Uri>(relaxed = true).apply {
                every { scheme } returns value.substringBefore(':', "")
                every { this@apply.toString() } returns value
            }
        }
    }

    fun unmockUriParsing() = unmockkStatic(Uri::class)
}
