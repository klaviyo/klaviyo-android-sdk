package com.klaviyo.coresdk.model

import com.klaviyo.coresdk.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ModelTests : BaseTest() {
    @Test
    fun `Merge profiles obeys expected precedence`() {
        val firstProfile = Profile().also {
            it.identifier = "other"
            it.email = EMAIL
        }

        val secondProfile = Profile().also {
            it.identifier = EXTERNAL_ID
            it.phoneNumber = PHONE
        }

        val mergedProfile = firstProfile.merge(secondProfile)

        assertEquals(EMAIL, mergedProfile.email)
        assertEquals(EXTERNAL_ID, mergedProfile.identifier)
        assertEquals(PHONE, mergedProfile.phoneNumber)
    }

    @Test
    fun `Profile represents appended properties`() {
        val appendKey = "key"
        val appendValue = "value"
        val profile = Profile().addAppendProperty(appendKey, appendValue)
        val appended = profile.toMap()["\$append"]

        assert(appended is HashMap<*, *>)
        assertEquals((appended as HashMap<*, *>)[appendKey], appendValue)
    }

    @Test
    fun `Get, set and unset`() {
        val event = Event("test")
        event[KlaviyoEventAttributeKey.VALUE] = "$1"
        event[KlaviyoEventAttributeKey.CUSTOM("custom")] = "custom"
        assertEquals("$1", event.toMap()[KlaviyoEventAttributeKey.VALUE.name])
        assertEquals("custom", event.toMap()["custom"])

        // Nulling should unset from the backing map
        event[KlaviyoEventAttributeKey.VALUE] = null
        event[KlaviyoEventAttributeKey.CUSTOM("custom")] = null

        assertEquals(false, event.toMap().containsKey(KlaviyoEventAttributeKey.VALUE.name))
        assertEquals(false, event.toMap().containsKey("custom"))
    }

    @Test
    fun `Overwriting a custom key`() {
        val event = Event("test")
        event[KlaviyoEventAttributeKey.CUSTOM("custom")] = "1"
        event[KlaviyoEventAttributeKey.CUSTOM("custom")] = "2"
        assertEquals("2", event.toMap()["custom"])
    }

    @Test
    fun `Profile properties are reflected in toMap representation`() {
        val profileMap = Profile().also {
            it.identifier = EXTERNAL_ID
            it.email = EMAIL
            it.phoneNumber = PHONE
            it.anonymousId = ANON_ID
            it.setProperty("string", "string")
            it.setProperty(KlaviyoProfileAttributeKey.CUSTOM("custom"), "custom")
        }.toMap()

        assertEquals(EXTERNAL_ID, profileMap[KlaviyoProfileAttributeKey.EXTERNAL_ID.name])
        assertEquals(EMAIL, profileMap[KlaviyoProfileAttributeKey.EMAIL.name])
        assertEquals(PHONE, profileMap[KlaviyoProfileAttributeKey.PHONE_NUMBER.name])
        assertEquals(ANON_ID, profileMap[KlaviyoProfileAttributeKey.ANONYMOUS_ID.name])
        assertEquals("string", profileMap["string"])
        assertEquals("custom", profileMap["custom"])
    }

    @Test
    fun `Event properties are reflected in toMap representation`() {
        val event = Event("test").also {
            it.setValue("$1")
            it.setProperty(KlaviyoEventAttributeKey.EVENT_ID, "id")
            it.setProperty(KlaviyoEventAttributeKey.CUSTOM("custom"), "custom")
        }

        val eventMap = event.toMap()

        assertEquals("test", event.type.name)
        assertEquals("$1", event.value)
        assertNull(eventMap["type"])
        assertEquals("id", eventMap[KlaviyoEventAttributeKey.EVENT_ID.name])
        assertEquals("$1", eventMap[KlaviyoEventAttributeKey.VALUE.name])
        assertEquals("custom", eventMap["custom"])
    }

    @Test
    fun `Keyword equality`() {
        val custProfile1 = KlaviyoProfileAttributeKey.CUSTOM("custom")
        val custProfile2 = KlaviyoProfileAttributeKey.CUSTOM("custom")
        val custEvent = KlaviyoEventAttributeKey.CUSTOM("custom")

        assertEquals(custProfile1, custProfile2)
        assertEquals(custProfile1, custEvent)
        assert(!custEvent.equals("custom"))
    }
}
