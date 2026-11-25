package com.klaviyo.inbox

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class InboxClickEventTest : BaseTest() {

    private val attribution = """
        {"tm":"tm-7","${'$'}message":"msg-1","Message Type":"campaign","Message Name":"BF Sale","c":"Abc123"}
    """.trimIndent()

    private val message = inboxMessageEntity(
        transmissionId = "tm-7",
        attributionJson = attribution,
        defaultDestination = InboxRecordDestination("deep_link", "myapp://sale"),
        actions = listOf(
            InboxRecordAction(
                "shop",
                "Shop now",
                InboxRecordDestination("deep_link", "myapp://shop")
            ),
            InboxRecordAction("site", "Visit", InboxRecordDestination("open_url", "https://a.com")),
            InboxRecordAction("open", "Open", InboxRecordDestination("open_app", null))
        )
    ).toInboxMessage()

    private fun Event.prop(key: EventKey) = this[key]

    private fun Event.custom(name: String) = this[EventKey.CUSTOM(name)]

    @Test
    fun `the metric is branded under the klaviyo service`() {
        val event = buildInboxClickEvent(message, null)!!

        assertEquals("Clicked App Inbox Message", event.metric.name)
        assertEquals("klaviyo", event.metric.service)
    }

    @Test
    fun `a body click reports the default destination`() {
        val event = buildInboxClickEvent(message, null)!!

        assertEquals("tm-7", event.prop(InboxClickProperty.TRANSMISSION_ID))
        assertEquals("deep_link", event.prop(InboxClickProperty.ACTION_TYPE))
        assertEquals("myapp://sale", event.prop(InboxClickProperty.ACTION_URL))
        assertNull(event.prop(InboxClickProperty.BUTTON_LABEL))
    }

    @Test
    fun `a button click reports the button's destination and label`() {
        val event = buildInboxClickEvent(message, message.actions[1])!!

        assertEquals("open_url", event.prop(InboxClickProperty.ACTION_TYPE))
        assertEquals("https://a.com", event.prop(InboxClickProperty.ACTION_URL))
        assertEquals("Visit", event.prop(InboxClickProperty.BUTTON_LABEL))
    }

    @Test
    fun `an open app button has no URL`() {
        val event = buildInboxClickEvent(message, message.actions[2])!!

        assertEquals("open_app", event.prop(InboxClickProperty.ACTION_TYPE))
        assertNull(event.prop(InboxClickProperty.ACTION_URL))
        assertEquals("Open", event.prop(InboxClickProperty.BUTTON_LABEL))
    }

    @Test
    fun `an unknown destination type is reported as received`() {
        val unknown = inboxMessageEntity(
            transmissionId = "tm-8",
            actions = listOf(
                InboxRecordAction("x", "X", InboxRecordDestination("future_type", "x://y"))
            )
        ).toInboxMessage()

        val event = buildInboxClickEvent(unknown, unknown.actions[0])!!

        assertEquals("future_type", event.prop(InboxClickProperty.ACTION_TYPE))
    }

    @Test
    fun `the event carries no element property`() {
        val event = buildInboxClickEvent(message)!!

        assertNull(event.custom("Element"))
    }

    @Test
    fun `attribution fields are copied from the stored _k`() {
        val event = buildInboxClickEvent(message)!!

        assertEquals("msg-1", event.prop(InboxClickProperty.MESSAGE))
        assertEquals("campaign", event.prop(InboxClickProperty.MESSAGE_TYPE))
        assertEquals("BF Sale", event.prop(InboxClickProperty.MESSAGE_NAME))
    }

    @Test
    fun `a missing attribution field is left off the event`() {
        val partial = inboxMessageEntity(
            transmissionId = "tm-7",
            attributionJson = """{"tm":"tm-7","Message Type":"flow"}"""
        ).toInboxMessage()

        val event = buildInboxClickEvent(partial)!!

        assertEquals("flow", event.prop(InboxClickProperty.MESSAGE_TYPE))
        assertNull(event.prop(InboxClickProperty.MESSAGE))
        assertNull(event.prop(InboxClickProperty.MESSAGE_NAME))
    }

    @Test
    fun `a message with no attribution still produces a click`() {
        val bare = inboxMessageEntity(transmissionId = "tm-7", attributionJson = null).toInboxMessage()

        val event = buildInboxClickEvent(bare)!!

        assertEquals("tm-7", event.prop(InboxClickProperty.TRANSMISSION_ID))
        assertNull(event.prop(InboxClickProperty.MESSAGE))
    }

    @Test
    fun `unreadable attribution is ignored with a warning`() {
        val broken = inboxMessageEntity(transmissionId = "tm-7", attributionJson = "{not json").toInboxMessage()

        val event = buildInboxClickEvent(broken)!!

        assertEquals("tm-7", event.prop(InboxClickProperty.TRANSMISSION_ID))
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `custom properties are added to the event`() {
        val event = buildInboxClickEvent(message, null, mapOf("screen" to "inbox", "position" to 3))!!

        assertEquals("inbox", event.custom("screen"))
        assertEquals(3, event.custom("position"))
    }

    @Test
    fun `a custom property cannot replace a built-in one`() {
        val event = buildInboxClickEvent(
            message,
            message.actions[0],
            mapOf(
                "Action Type" to "forged",
                "Inbox Transmission ID" to "forged",
                "Button Label" to "forged",
                "\$message" to "forged",
                "\$event_id" to "forged",
                "screen" to "inbox"
            )
        )!!

        assertEquals("deep_link", event.prop(InboxClickProperty.ACTION_TYPE))
        assertEquals("tm-7", event.prop(InboxClickProperty.TRANSMISSION_ID))
        assertEquals("Shop now", event.prop(InboxClickProperty.BUTTON_LABEL))
        assertEquals("msg-1", event.prop(InboxClickProperty.MESSAGE))
        assertNotEquals("forged", event.uniqueId)
        assertEquals("inbox", event.custom("screen"))
    }

    @Test
    fun `a reserved custom key is dropped even when the SDK has no value for it`() {
        val bare = inboxMessageEntity(transmissionId = "tm-7", attributionJson = null).toInboxMessage()

        val event = buildInboxClickEvent(bare, null, mapOf("\$message" to "forged"))!!

        assertNull(event.prop(InboxClickProperty.MESSAGE))
    }

    @Test
    fun `each click gets its own unique ID`() {
        val first = buildInboxClickEvent(message, null)!!
        val second = buildInboxClickEvent(message, null)!!

        assertNotNull(first.uniqueId)
        assertNotEquals(first.uniqueId, second.uniqueId)
    }

    @Test
    fun `a message ID outside the limit is dropped`() {
        val tooLong = inboxMessageEntity(transmissionId = "x".repeat(65)).toInboxMessage()

        assertNull(buildInboxClickEvent(tooLong, null))
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `a URL over the limit is dropped`() {
        val longUrl = inboxMessageEntity(
            defaultDestination = InboxRecordDestination(
                "open_url",
                "https://a.com/" + "x".repeat(2048)
            )
        ).toInboxMessage()

        assertNull(buildInboxClickEvent(longUrl, null))
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `a button label over the limit is dropped`() {
        val longLabel = inboxMessageEntity(
            actions = listOf(
                InboxRecordAction("a", "x".repeat(256), InboxRecordDestination("open_app", null))
            )
        ).toInboxMessage()

        assertNull(buildInboxClickEvent(longLabel, longLabel.actions[0]))
        verify { spyLog.warning(any(), null) }
    }
}
