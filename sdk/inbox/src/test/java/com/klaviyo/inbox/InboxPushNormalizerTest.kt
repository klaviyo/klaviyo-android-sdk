package com.klaviyo.inbox

import com.klaviyo.core.Constants
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.inbox.InboxPushFixtures.FCM_MESSAGE_ID
import com.klaviyo.inbox.InboxPushFixtures.SENT_AT_MILLIS
import com.klaviyo.inbox.InboxPushFixtures.TRANSMISSION_ID
import com.klaviyo.inbox.InboxPushFixtures.actionButtons
import com.klaviyo.inbox.InboxPushFixtures.canonicalPayload
import com.klaviyo.inbox.InboxPushFixtures.remoteMessage
import com.klaviyo.inbox.InboxPushFixtures.tracking
import com.klaviyo.inbox.InboxPushNormalizer.toInboxCaptureRecord
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class InboxPushNormalizerTest : BaseTest() {

    private val receivedAt = 1_791_313_260_000L

    @Before
    override fun setup() {
        super.setup()
        InboxPushFixtures.mockUriParsing()
    }

    @After
    override fun cleanup() {
        InboxPushFixtures.unmockUriParsing()
        super.cleanup()
    }

    private fun normalize(
        data: Map<String, String> = canonicalPayload(),
        messageId: String? = FCM_MESSAGE_ID
    ) = remoteMessage(data, messageId).toInboxCaptureRecord(receivedAt)

    private fun canonicalWith(vararg changes: Pair<String, String?>) = canonicalPayload().apply {
        changes.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
    }

    @Test
    fun `canonical payload maps every field`() {
        val expected = InboxCaptureRecord(
            dedupeKey = TRANSMISSION_ID,
            title = "Sale today",
            body = "Everything is 20% off",
            defaultDestination = InboxRecordDestination(
                Constants.ACTION_TYPE_DEEP_LINK,
                "myapp://sale"
            ),
            mediaUrl = "https://cdn.example.com/sale.png",
            mediaType = "png",
            customData = mapOf("promo" to "SALE20"),
            actions = listOf(
                InboxRecordAction(
                    "shop",
                    "Shop",
                    InboxRecordDestination(Constants.ACTION_TYPE_DEEP_LINK, "myapp://shop")
                ),
                InboxRecordAction(
                    "site",
                    "Website",
                    InboxRecordDestination(Constants.ACTION_TYPE_OPEN_URL, "https://example.com")
                ),
                InboxRecordAction(
                    "open",
                    "Open",
                    InboxRecordDestination(Constants.ACTION_TYPE_OPEN_APP, null)
                )
            ),
            sentAt = SENT_AT_MILLIS,
            collapseId = "sale-tag",
            receivedAt = receivedAt,
            attribution = InboxAttribution(
                transmissionId = TRANSMISSION_ID,
                message = "message-1",
                timestamp = "2026-10-06T19:00:00.123456+00:00",
                messageType = "campaign",
                campaign = "campaign-1",
                variation = "variation-1",
                messageVariation = "message-variation-1",
                campaignAudience = "audience-1"
            )
        )

        val actual = normalize()?.let {
            it.copy(
                attribution = it.attribution.copy(rawTrackingJson = null)
            )
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `raw tracking data keeps every key except the push token`() {
        val raw = JSONObject(normalize()?.attribution?.rawTrackingJson.orEmpty())

        assertFalse(raw.has("pt"))
        assertEquals(TRANSMISSION_ID, raw.getString("tm"))
        assertEquals("campaign-1", raw.getString("\$campaign"))
    }

    @Test
    fun `title-only and body-only payloads are captured`() {
        assertEquals("Sale today", normalize(canonicalWith("body" to null))?.title)
        assertEquals("Everything is 20% off", normalize(canonicalWith("title" to null))?.body)
    }

    @Test
    fun `a push without title or body is not captured`() {
        assertNull(normalize(canonicalWith("title" to null, "body" to null)))
    }

    @Test
    fun `web_url is the default destination when there is no deep link`() {
        val record = normalize(
            canonicalWith("url" to null, "web_url" to "https://example.com/sale")
        )

        assertEquals(
            InboxRecordDestination(Constants.ACTION_TYPE_OPEN_URL, "https://example.com/sale"),
            record?.defaultDestination
        )
    }

    @Test
    fun `a disallowed web_url scheme falls back to opening the app`() {
        val record = normalize(canonicalWith("url" to null, "web_url" to "javascript:alert(1)"))

        assertEquals(
            InboxRecordDestination(Constants.ACTION_TYPE_OPEN_APP, null),
            record?.defaultDestination
        )
    }

    @Test
    fun `unknown and invalid actions are dropped and at most three are kept`() {
        val buttons = actionButtons(
            mapOf("id" to "a", "label" to "A", "action" to "share"),
            mapOf(
                "id" to "b",
                "label" to "B",
                "action" to "open_url",
                "url" to "javascript:alert(1)"
            ),
            mapOf("id" to "c", "label" to "C", "action" to "open_app"),
            mapOf("id" to "d", "label" to "D", "action" to "open_app"),
            mapOf("id" to "e", "label" to "E", "action" to "open_app"),
            mapOf("id" to "f", "label" to "F", "action" to "open_app")
        )

        val record = normalize(canonicalWith("action_buttons" to buttons))

        assertEquals(listOf("c", "d", "e"), record?.actions?.map { it.id })
    }

    @Test
    fun `missing optional fields map to empty values`() {
        val record = normalize(
            canonicalWith(
                "url" to null,
                "image_url" to null,
                "image_type" to null,
                "key_value_pairs" to null,
                "action_buttons" to null,
                "notification_tag" to null
            )
        )

        assertEquals(
            InboxRecordDestination(Constants.ACTION_TYPE_OPEN_APP, null),
            record?.defaultDestination
        )
        assertNull(record?.mediaUrl)
        assertNull(record?.mediaType)
        assertEquals(emptyMap<String, String>(), record?.customData)
        assertEquals(emptyList<InboxRecordAction>(), record?.actions)
        assertNull(record?.collapseId)
    }

    @Test
    fun `malformed key_value_pairs map to empty custom data`() {
        assertEquals(
            emptyMap<String, String>(),
            normalize(canonicalWith("key_value_pairs" to "{not json"))?.customData
        )
    }

    @Test
    fun `a push without a transmission ID deduplicates on the FCM message ID`() {
        val record = normalize(canonicalWith("_k" to tracking("tm" to null)))

        assertEquals("fcm:$FCM_MESSAGE_ID", record?.dedupeKey)
        assertNull(record?.attribution?.transmissionId)
        assertEquals("campaign-1", record?.attribution?.campaign)
    }

    @Test
    fun `a push with neither a transmission ID nor an FCM message ID is not captured`() {
        assertNull(normalize(canonicalWith("_k" to tracking("tm" to null)), messageId = null))
    }

    @Test
    fun `malformed tracking data is captured without attribution or send time`() {
        val record = normalize(canonicalWith("_k" to "{not json"))

        assertNotNull(record)
        assertEquals("fcm:$FCM_MESSAGE_ID", record?.dedupeKey)
        assertEquals(InboxAttribution(), record?.attribution)
        assertNull(record?.sentAt)
        assertEquals("Sale today", record?.title)
    }

    @Test
    fun `an unparseable timestamp leaves the send time empty`() {
        assertNull(normalize(canonicalWith("_k" to tracking("timestamp" to "yesterday")))?.sentAt)
    }

    @Test
    fun `the short message type key is used when Message Type is absent`() {
        val record = normalize(
            canonicalWith("_k" to tracking("Message Type" to null, "x" to "flow"))
        )

        assertEquals("flow", record?.attribution?.messageType)
    }
}
