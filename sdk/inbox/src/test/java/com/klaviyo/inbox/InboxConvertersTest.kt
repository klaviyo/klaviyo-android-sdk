package com.klaviyo.inbox

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class InboxConvertersTest : BaseTest() {

    private val converters = InboxConverters()

    private val actions = listOf(
        InboxRecordAction("shop", "Shop", InboxRecordDestination("deep_link", "myapp://shop")),
        InboxRecordAction("open", "Open", InboxRecordDestination("open_app", null))
    )

    // Stored v1 layout. Rows written by a released SDK must keep reading back, so changing these
    // fixtures means adding a migration.
    private val actionsV1Json = """
        [
          {"id":"shop","label":"Shop","type":"deep_link","url":"myapp://shop"},
          {"id":"open","label":"Open","type":"open_app","url":null}
        ]
    """.trimIndent()

    private val customDataV1Json = """{"promo":"SALE20","tier":"gold"}"""

    @Test
    fun `actions are written in the v1 layout`() {
        assertJsonEquals(actionsV1Json, converters.actionsToJson(actions))
    }

    @Test
    fun `actions are read from the v1 layout`() {
        assertEquals(actions, converters.jsonToActions(actionsV1Json))
    }

    @Test
    fun `an action without a url key reads as having no url`() {
        val json = """[{"id":"open","label":"Open","type":"open_app"}]"""

        assertEquals(
            listOf(InboxRecordAction("open", "Open", InboxRecordDestination("open_app", null))),
            converters.jsonToActions(json)
        )
    }

    @Test
    fun `actions round trip with their order and null urls`() {
        val json = converters.actionsToJson(actions)

        assertEquals(actions, converters.jsonToActions(json))
    }

    @Test
    fun `no actions round trip as an empty list`() {
        assertEquals(
            emptyList<InboxRecordAction>(),
            converters.jsonToActions(converters.actionsToJson(emptyList()))
        )
    }

    @Test
    fun `custom data is written in the v1 layout`() {
        assertJsonEquals(
            customDataV1Json,
            converters.customDataToJson(mapOf("promo" to "SALE20", "tier" to "gold"))
        )
    }

    @Test
    fun `custom data is read from the v1 layout`() {
        assertEquals(
            mapOf("promo" to "SALE20", "tier" to "gold"),
            converters.jsonToCustomData(customDataV1Json)
        )
    }

    @Test
    fun `custom data round trips`() {
        val customData = mapOf("promo" to "SALE20", "tier" to "gold")

        assertEquals(
            customData,
            converters.jsonToCustomData(converters.customDataToJson(customData))
        )
    }

    @Test
    fun `no custom data round trips as an empty map`() {
        assertEquals(
            emptyMap<String, String>(),
            converters.jsonToCustomData(converters.customDataToJson(emptyMap()))
        )
    }

    @Test
    fun `malformed stored actions read as empty and log a warning`() {
        assertEquals(emptyList<InboxRecordAction>(), converters.jsonToActions("{not json"))

        verify { spyLog.warning(any(), any()) }
    }

    @Test
    fun `an action missing a required key reads the whole list as empty and logs a warning`() {
        val withoutKey = mapOf(
            "id" to """[{"label":"Shop","type":"open_app"}]""",
            "label" to """[{"id":"shop","type":"open_app"}]""",
            "type" to """[{"id":"shop","label":"Shop"}]"""
        )

        withoutKey.forEach { (missing, json) ->
            assertEquals(
                "missing $missing",
                emptyList<InboxRecordAction>(),
                converters.jsonToActions(json)
            )
        }

        verify(exactly = withoutKey.size) { spyLog.warning(any(), any()) }
    }

    @Test
    fun `malformed stored custom data reads as empty and logs a warning`() {
        assertEquals(emptyMap<String, String>(), converters.jsonToCustomData("[1,2"))

        verify { spyLog.warning(any(), any()) }
    }

    /**
     * Compares structure and values, so object key order does not matter
     */
    private fun assertJsonEquals(expected: String, actual: String) {
        val matches = if (expected.trimStart().startsWith("[")) {
            JSONArray(expected).similar(JSONArray(actual))
        } else {
            JSONObject(expected).similar(JSONObject(actual))
        }

        assertTrue("Expected $expected but was $actual", matches)
    }
}
