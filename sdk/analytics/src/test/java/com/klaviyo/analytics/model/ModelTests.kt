package com.klaviyo.analytics.model

import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ModelTests : BaseTest() {
    @Test
    fun `Merge profiles obeys expected precedence`() {
        val firstProfile = Profile().also {
            it.externalId = "other"
            it.email = EMAIL
        }

        val secondProfile = Profile().also {
            it.externalId = EXTERNAL_ID
            it.phoneNumber = PHONE
        }

        val mergedProfile = firstProfile.merge(secondProfile)

        assertEquals(
            EMAIL,
            mergedProfile.email
        )
        assertEquals(
            EXTERNAL_ID,
            mergedProfile.externalId
        )
        assertEquals(
            PHONE,
            mergedProfile.phoneNumber
        )
    }

    @Test
    fun `Get, set and unset`() {
        val event = Event("test")
        event[EventKey.VALUE] = "$1"
        event[EventKey.CUSTOM("custom")] = "custom"
        event["string"] = "string"
        assertEquals("$1", event.toMap()[EventKey.VALUE.name])
        assertEquals("custom", event.toMap()["custom"])
        assertEquals("string", event.toMap()["string"])

        // Nulling should unset from the backing map
        event[EventKey.VALUE] = null
        event[EventKey.CUSTOM("custom")] = null

        assertEquals(false, event.toMap().containsKey(EventKey.VALUE.name))
        assertEquals(false, event.toMap().containsKey("custom"))
    }

    @Test
    fun `Overwriting a custom key`() {
        val event = Event("test")
        event[EventKey.CUSTOM("custom")] = "1"
        event[EventKey.CUSTOM("custom")] = "2"
        assertEquals("2", event.toMap()["custom"])
    }

    @Test
    fun `Profile properties are reflected in toMap representation`() {
        val profileMap = Profile().also {
            it.externalId = EXTERNAL_ID
            it.email = EMAIL
            it.phoneNumber = PHONE
            it.anonymousId = ANON_ID
            it.setProperty("string", "string")
            it.setProperty(ProfileKey.CUSTOM("custom"), "custom")
        }.toMap()

        assertEquals(EXTERNAL_ID, profileMap[ProfileKey.EXTERNAL_ID.name])
        assertEquals(EMAIL, profileMap[ProfileKey.EMAIL.name])
        assertEquals(PHONE, profileMap[ProfileKey.PHONE_NUMBER.name])
        assertEquals(ANON_ID, profileMap[ProfileKey.ANONYMOUS_ID.name])
        assertEquals("string", profileMap["string"])
        assertEquals("custom", profileMap["custom"])
    }

    @Test
    fun `Type of special model properties is enforced without crashing`() {
        val profile = Profile(
            mapOf(
                ProfileKey.EXTERNAL_ID to 0,
                ProfileKey.EMAIL to 0,
                ProfileKey.PHONE_NUMBER to 0
            )
        )

        assertEquals("0", profile.externalId)
        assertEquals("0", profile.email)
        assertEquals("0", profile.phoneNumber)

        val event = Event("test", mapOf(EventKey.VALUE to 0))
        assertEquals(0.0, event.value)
    }

    @Test
    fun `Event properties are reflected in toMap representation`() {
        val event = Event("test").also {
            it.setValue(1.0)
            it.setProperty(EventKey.EVENT_ID, "id")
            it.setProperty(EventKey.CUSTOM("custom"), "custom")
        }

        val eventMap = event.toMap()

        assertEquals("test", event.type.name)
        assertEquals(1.0, event.value)
        assertNull(eventMap["type"])
        assertEquals("id", eventMap[EventKey.EVENT_ID.name])
        assertEquals(1.0, eventMap[EventKey.VALUE.name])
        assertEquals("custom", eventMap["custom"])
    }

    @Test
    fun `Keyword equality`() {
        val custProfile1 = ProfileKey.CUSTOM("custom")
        val custProfile2 = ProfileKey.CUSTOM("custom")
        val custEvent = EventKey.CUSTOM("custom")

        assertEquals(custProfile1, custProfile2)
        assertEquals(custProfile1, custEvent)
        assert(!custEvent.equals("custom"))
    }
}
