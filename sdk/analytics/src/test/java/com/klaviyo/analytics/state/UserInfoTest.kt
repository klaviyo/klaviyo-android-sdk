package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import java.io.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

internal class UserInfoTest : BaseTest() {

    private lateinit var userInfo: UserInfo

    @Before
    override fun setup() {
        super.setup()
        userInfo = UserInfo().apply { reset() }
    }

    @Test
    fun `UserInfo is convertible to Profile`() {
        userInfo.externalId = EXTERNAL_ID
        userInfo.email = EMAIL
        userInfo.phoneNumber = PHONE
        assertProfileIdentifiers(userInfo.get())
        assertUserInfoIdentifiers()
    }

    @Test
    fun `Create and store a new UUID if one does not exists in data store`() {
        val anonId = userInfo.anonymousId
        val fetched = dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name)
        assertEquals(anonId, fetched)
    }

    @Test
    fun `Do not create new UUID if one exists in data store`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS_ID.name, ANON_ID)
        assertEquals(ANON_ID, userInfo.anonymousId)
    }

    @Test
    fun `Only read properties from data store once`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS_ID.name, ANON_ID)
        dataStoreSpy.store(ProfileKey.EMAIL.name, EMAIL)
        dataStoreSpy.store(ProfileKey.EXTERNAL_ID.name, EXTERNAL_ID)
        dataStoreSpy.store(ProfileKey.PHONE_NUMBER.name, PHONE)

        userInfo.anonymousId
        assertEquals(ANON_ID, userInfo.anonymousId)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name) }

        userInfo.email
        assertEquals(EMAIL, userInfo.email)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EMAIL.name) }

        userInfo.externalId
        assertEquals(EXTERNAL_ID, userInfo.externalId)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EXTERNAL_ID.name) }

        userInfo.phoneNumber
        assertEquals(PHONE, userInfo.phoneNumber)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.PHONE_NUMBER.name) }
    }

    @Test
    fun `Anonymous ID lifecycle`() {
        // Should be null after a reset...
        val initialAnonId = dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name)
        assertNull(initialAnonId)

        // Start tracking a new anon ID and it should be persisted
        val firstAnonId = userInfo.anonymousId
        assertEquals(firstAnonId, dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))

        // Reset again should nullify in data store
        userInfo.reset()
        assertNull(dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))

        // Start tracking again should generate another new anon ID
        val newAnonId = userInfo.anonymousId
        assertNotEquals(firstAnonId, newAnonId)
        assertEquals(newAnonId, dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))
    }

    @Test
    fun `Catches bad profile attributes persisted to disk`() {
        dataStoreSpy.store(
            "attributes",
            """invalid_json""".trimIndent()
        )

        val profile = UserInfo().get(true)
        assertEquals(0, profile.attributes.propertyCount())
        verify { logSpy.warning(any(), any()) }
    }

    @Test
    fun `Deserializes profile attributes from disk`() {
        dataStoreSpy.store(
            "attributes",
            """
            {
              "first_name": "Kermit",
              "string": "str",
              "number": 1,
              "double": 1.0,
              "bool": true,
              "array": [
                1,
                "2",
                {
                  "k": "v"
                }
              ],
              "object": {
                "string": "str",
                "number": 1,
                "double": 1.0,
                "bool": true,
                "sub_object": {"abc": "xyz"},
                "sub_array": [
                  "test",
                  2
                ]
              }
            }
            """.trimIndent()
        )

        val profile = UserInfo().get(true)
        assertEquals(7, profile.attributes.propertyCount())
        assertEquals("Kermit", profile[ProfileKey.FIRST_NAME])
        assertEquals("str", profile[ProfileKey.CUSTOM("string")])
        assertEquals(1, profile[ProfileKey.CUSTOM("number")])
        assertEquals(1.0.toBigDecimal(), profile[ProfileKey.CUSTOM("double")])
        assertEquals(true, profile[ProfileKey.CUSTOM("bool")])
        val array = profile[ProfileKey.CUSTOM("array")]
        val obj = profile[ProfileKey.CUSTOM("object")]

        if (array is Array<*> && array.isArrayOf<Serializable>()) {
            assertEquals(1, array[0])
            assertEquals("2", array[1])
            val subObj = array[2]

            if (subObj is HashMap<*, *>) {
                assertEquals("v", subObj["k"])
            } else {
                fail("Object did not decode")
            }
        } else {
            fail("Array did not decode")
        }

        if (obj is HashMap<*, *>) {
            assertEquals("str", obj["string"])
            assertEquals(1, obj["number"])
            assertEquals(1.0.toBigDecimal(), obj["double"])
            assertEquals(true, obj["bool"])
            val subObj = obj["sub_object"]
            val subArray = obj["sub_array"]

            if (subObj is HashMap<*, *>) {
                assertEquals("xyz", subObj["abc"])
            } else {
                fail("Sub-object did not decode")
            }

            if (subArray is Array<*> && subArray.isArrayOf<Serializable>()) {
                assertEquals("test", subArray[0])
                assertEquals(2, subArray[1])
            } else {
                fail("Sub-array did not decode")
            }
        } else {
            fail("Object did not decode")
        }
    }

    private fun assertProfileIdentifiers(profile: Profile) {
        assert(profile.externalId == EXTERNAL_ID)
        assert(profile.email == EMAIL)
        assert(profile.phoneNumber == PHONE)
        assert(profile.anonymousId == userInfo.anonymousId)
        assert(profile.toMap().count() == 4) // shouldn't contain any extras
    }

    private fun assertUserInfoIdentifiers() {
        assertEquals(EXTERNAL_ID, userInfo.externalId)
        assertEquals(EMAIL, userInfo.email)
        assertEquals(PHONE, userInfo.phoneNumber)
    }
}
