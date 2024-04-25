package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.PROFILE_ATTRIBUTES
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import java.io.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PersistentObservableProfileTest : BaseTest() {

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

        val delegatedProfile by PersistentObservableProfile(
            PROFILE_ATTRIBUTES
        )

        val profile = delegatedProfile?.copy()

        if (profile == null) {
            fail("Profile was not fetched from data store")
            return
        }

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

    @Test
    fun `Catches bad profile attributes persisted to disk`() {
        dataStoreSpy.store(
            "attributes",
            """invalid_json""".trimIndent()
        )

        val profile = KlaviyoState().get(true)
        assertEquals(0, profile.attributes.propertyCount())
        verify { logSpy.warning(any(), any()) }
    }

    @Test
    fun `Catches bad json persisted to disk`() {
        dataStoreSpy.store(
            "attributes",
            """{]""".trimIndent()
        )

        val profile = KlaviyoState().get(true)
        assertEquals(0, profile.attributes.propertyCount())
        verify { logSpy.warning(any(), any()) }
    }
}
