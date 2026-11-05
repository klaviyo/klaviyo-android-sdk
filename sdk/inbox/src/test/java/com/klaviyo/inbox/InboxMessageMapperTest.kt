package com.klaviyo.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class InboxMessageMapperTest {

    /**
     * Every field holds a different value, so a swapped pair fails
     */
    private val entity = inboxMessageEntity(
        transmissionId = "tm-7",
        companyId = "company",
        title = "Sale today",
        body = "Everything is 20% off",
        defaultDestination = InboxRecordDestination("deep_link", "myapp://sale"),
        mediaUrl = "https://cdn.example.com/sale.png",
        mediaType = "png",
        customData = mapOf("promo" to "SALE20"),
        actions = listOf(
            InboxRecordAction("shop", "Shop", InboxRecordDestination("deep_link", "myapp://shop")),
            InboxRecordAction("site", "Visit", InboxRecordDestination("open_url", "https://a.com")),
            InboxRecordAction("open", "Open", InboxRecordDestination("open_app", null))
        ),
        sentAt = 5_000L,
        collapseId = "sale",
        badge = 3,
        receivedAt = 6_000L,
        isRead = true,
        isArchived = true
    )

    @Test
    fun `a stored row maps every field to the public message`() {
        val message = entity.toInboxMessage()

        assertEquals("tm-7", message.id)
        assertEquals("Sale today", message.title)
        assertEquals("Everything is 20% off", message.body)
        assertEquals("https://cdn.example.com/sale.png", message.mediaUrl)
        assertEquals("png", message.mediaType)
        assertEquals(mapOf("promo" to "SALE20"), message.customData)
        assertEquals(5_000L, message.sentAt)
        assertEquals(6_000L, message.receivedAt)
        assertEquals("sale", message.collapseId)
        assertEquals(3, message.badge)
        assertEquals(true, message.isRead)
        assertEquals(true, message.isArchived)
        assertEquals(InboxMessageSource.CAPTURED, message.source)
    }

    @Test
    fun `a deep link default destination keeps its type and URL`() {
        val destination = entity.toInboxMessage().defaultDestination

        assertEquals(InboxDestinationType.DEEP_LINK, destination.type)
        assertEquals("myapp://sale", destination.url)
    }

    @Test
    fun `every action keeps its ID, label, type and URL in order`() {
        val actions = entity.toInboxMessage().actions

        assertEquals(listOf("shop", "site", "open"), actions.map { it.id })
        assertEquals(listOf("Shop", "Visit", "Open"), actions.map { it.label })
        assertEquals(
            listOf(
                InboxDestinationType.DEEP_LINK,
                InboxDestinationType.OPEN_URL,
                InboxDestinationType.OPEN_APP
            ),
            actions.map { it.destination.type }
        )
        assertEquals(
            listOf("myapp://shop", "https://a.com", null),
            actions.map { it.destination.url }
        )
    }

    @Test
    fun `a message with only the required fields maps its absent ones to null`() {
        val message = inboxMessageEntity(title = null).toInboxMessage()

        assertNull(message.title)
        assertNull(message.body)
        assertNull(message.mediaUrl)
        assertNull(message.mediaType)
        assertNull(message.sentAt)
        assertNull(message.collapseId)
        assertNull(message.badge)
        assertEquals(emptyList<InboxAction>(), message.actions)
        assertEquals(false, message.isRead)
        assertEquals(false, message.isArchived)
        assertEquals(InboxDestinationType.OPEN_APP, message.defaultDestination.type)
        assertNull(message.defaultDestination.url)
    }
}
