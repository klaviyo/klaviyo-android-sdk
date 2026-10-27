package com.klaviyo.inbox

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

internal class InboxConvertersTest : BaseTest() {

    private val converters = InboxConverters()

    private val actions = listOf(
        InboxRecordAction("shop", "Shop", InboxRecordDestination("deep_link", "myapp://shop")),
        InboxRecordAction("open", "Open", InboxRecordDestination("open_app", null))
    )

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
}
