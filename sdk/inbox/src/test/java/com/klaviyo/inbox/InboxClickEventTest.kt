package com.klaviyo.inbox

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class InboxClickEventTest : BaseTest() {

    private val message = inboxMessageEntity(
        transmissionId = "tm-7",
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

    @Test
    fun `the metric is branded under the klaviyo service`() {
        val event = buildInboxClickEvent(message, null, null)!!

        assertEquals("\$clicked_app_inbox_message", event.metric.name)
        assertEquals("klaviyo", event.metric.service)
    }

    @Test
    fun `the metric key is dollar-prefixed so it takes the priority queue path`() {
        assertTrue(InboxClickMetric.name.startsWith("$"))
    }

    @Test
    fun `a body click reports the default destination`() {
        val event = buildInboxClickEvent(message, null, null)!!

        assertEquals("tm-7", event.prop(InboxClickProperty.MESSAGE_ID))
        assertEquals("body", event.prop(InboxClickProperty.ELEMENT))
        assertEquals("deep_link", event.prop(InboxClickProperty.ACTION_TYPE))
        assertEquals("myapp://sale", event.prop(InboxClickProperty.ACTION_URL))
        assertNull(event.prop(InboxClickProperty.BUTTON_LABEL))
    }

    @Test
    fun `a button click reports the button's destination and label`() {
        val event = buildInboxClickEvent(message, message.actions[1], null)!!

        assertEquals("button", event.prop(InboxClickProperty.ELEMENT))
        assertEquals("open_url", event.prop(InboxClickProperty.ACTION_TYPE))
        assertEquals("https://a.com", event.prop(InboxClickProperty.ACTION_URL))
        assertEquals("Visit", event.prop(InboxClickProperty.BUTTON_LABEL))
    }

    @Test
    fun `an open app button has no URL`() {
        val event = buildInboxClickEvent(message, message.actions[2], null)!!

        assertEquals("open_app", event.prop(InboxClickProperty.ACTION_TYPE))
        assertNull(event.prop(InboxClickProperty.ACTION_URL))
        assertEquals("Open", event.prop(InboxClickProperty.BUTTON_LABEL))
    }

    @Test
    fun `an image click reports the default destination without a label`() {
        val event = buildInboxClickEvent(message, null, InboxClickElement.IMAGE)!!

        assertEquals("image", event.prop(InboxClickProperty.ELEMENT))
        assertEquals("myapp://sale", event.prop(InboxClickProperty.ACTION_URL))
        assertNull(event.prop(InboxClickProperty.BUTTON_LABEL))
    }

    @Test
    fun `an unknown destination type is reported as received`() {
        val unknown = inboxMessageEntity(
            transmissionId = "tm-8",
            actions = listOf(
                InboxRecordAction("x", "X", InboxRecordDestination("future_type", "x://y"))
            )
        ).toInboxMessage()

        val event = buildInboxClickEvent(unknown, unknown.actions[0], null)!!

        assertEquals("future_type", event.prop(InboxClickProperty.ACTION_TYPE))
    }

    @Test
    fun `each click gets its own unique ID`() {
        val first = buildInboxClickEvent(message, null, null)!!
        val second = buildInboxClickEvent(message, null, null)!!

        assertNotNull(first.uniqueId)
        assertNotEquals(first.uniqueId, second.uniqueId)
    }

    @Test
    fun `a button click without an action is dropped`() {
        assertNull(buildInboxClickEvent(message, null, InboxClickElement.BUTTON))
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `a message ID outside the limit is dropped`() {
        val tooLong = inboxMessageEntity(transmissionId = "x".repeat(65)).toInboxMessage()

        assertNull(buildInboxClickEvent(tooLong, null, null))
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

        assertNull(buildInboxClickEvent(longUrl, null, null))
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `a button label over the limit is dropped`() {
        val longLabel = inboxMessageEntity(
            actions = listOf(
                InboxRecordAction("a", "x".repeat(256), InboxRecordDestination("open_app", null))
            )
        ).toInboxMessage()

        assertNull(buildInboxClickEvent(longLabel, longLabel.actions[0], null))
        verify { spyLog.warning(any(), null) }
    }
}
