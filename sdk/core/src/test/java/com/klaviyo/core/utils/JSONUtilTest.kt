package com.klaviyo.core.utils

import com.klaviyo.core.utils.JSONUtil.toArray
import com.klaviyo.core.utils.JSONUtil.toHashMap
import kotlin.collections.get
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JSONUtilTest {

    @Test
    fun `toHashMap converts simple JSON object with strings`() {
        val json = JSONObject("""{"key1":"value1","key2":"value2"}""")
        val map = json.toHashMap()

        assertEquals(2, map.size)
        assertEquals("value1", map["key1"])
        assertEquals("value2", map["key2"])
    }

    @Test
    fun `toHashMap handles various primitive types`() {
        val json = JSONObject()
        json.put("stringValue", "test")
        json.put("intValue", 42)
        json.put("doubleValue", 3.14)
        json.put("boolValue", true)
        json.put("nullValue", JSONObject.NULL)

        val map = json.toHashMap()

        assertEquals("test", map["stringValue"])
        assertEquals(42, map["intValue"])
        assertEquals(3.14, map["doubleValue"])
        assertEquals(true, map["boolValue"])
        assertNull(map["nullValue"])
    }

    @Test
    fun `toHashMap handles nested objects`() {
        val json = JSONObject("""{"nested":{"innerKey":"innerValue"}}""")
        val map = json.toHashMap()

        assertTrue(map["nested"] is HashMap<*, *>)
        val nestedMap = map["nested"] as HashMap<*, *>
        assertEquals("innerValue", nestedMap["innerKey"])
    }

    @Test
    fun `toHashMap handles arrays`() {
        val json = JSONObject("""{"array":[1,2,3]}""")
        val map = json.toHashMap()

        assertTrue(map["array"] is Array<*>)
        val array = map["array"] as Array<*>
        assertEquals(3, array.size)
        assertEquals(1, array[0])
        assertEquals(2, array[1])
        assertEquals(3, array[2])
    }

    @Test
    fun `toArray converts JSON array to Kotlin array`() {
        val jsonArray = JSONArray("""[1,2,3,"test",true]""")
        val array = jsonArray.toArray()

        assertEquals(5, array.size)
        assertEquals(1, array[0])
        assertEquals(2, array[1])
        assertEquals(3, array[2])
        assertEquals("test", array[3])
        assertEquals(true, array[4])
    }

    @Test
    fun `toArray handles nested arrays`() {
        val jsonArray = JSONArray("""[[1,2],[3,4]]""")
        val array = jsonArray.toArray()

        assertEquals(2, array.size)
        assertTrue(array[0] is Array<*>)
        assertTrue(array[1] is Array<*>)

        val first = array[0] as Array<*>
        val second = array[1] as Array<*>

        assertEquals(1, first[0])
        assertEquals(2, first[1])
        assertEquals(3, second[0])
        assertEquals(4, second[1])
    }

    @Test
    fun `toArray handles objects in arrays`() {
        val jsonArray = JSONArray("""[{"key":"value"}]""")
        val array = jsonArray.toArray()

        assertEquals(1, array.size)
        assertTrue(array[0] is HashMap<*, *>)

        val obj = array[0] as HashMap<*, *>
        assertEquals("value", obj["key"])
    }

    @Test
    fun `toHashMap handles complex nested structures`() {
        val json = JSONObject(
            """{
                "string": "value",
                "number": 123,
                "nested": {
                    "array": [1, 2, {"innerKey": "innerValue"}]
                }
            }"""
        )
        val map = json.toHashMap()

        assertEquals("value", map["string"])
        assertEquals(123, map["number"])

        assertTrue(map["nested"] is HashMap<*, *>)
        val nested = map["nested"] as HashMap<*, *>

        assertTrue(nested["array"] is Array<*>)
        val array = nested["array"] as Array<*>
        assertEquals(3, array.size)
        assertEquals(1, array[0])
        assertEquals(2, array[1])

        assertTrue(array[2] is HashMap<*, *>)
        val innerObj = array[2] as HashMap<*, *>
        assertEquals("innerValue", innerObj["innerKey"])
    }
}
