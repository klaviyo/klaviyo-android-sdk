package com.klaviyo.messaging

import com.klaviyo.analytics.model.EventKey
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.*
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

class FormsUtilityTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
    }

    @After
    override fun cleanup() {
        super.cleanup()
    }

    @Test
    fun `test getProperties successfully parses properties`() {
        // Setup
        val json = JSONObject()
        val properties = JSONObject()
        properties.put("key1", "value1")
        properties.put("key2", "value2")
        json.put("properties", properties)

        // Act
        val result = json.getProperties()

        // Assert
        assertEquals(2, result.size)
        assertEquals("value1", result[EventKey.CUSTOM("key1")])
        assertEquals("value2", result[EventKey.CUSTOM("key2")])
    }

    @Test
    fun `test getProperties logs error on exception`() {
        // Setup
        val json = JSONObject("{\"properties\": {\"key1\": 123}}") // Non-string value
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        // Act
        json.getProperties()

        // Assert
        verify { Registry.log.error(any(), any<Exception>()) }
    }

    @Test
    fun `test decodeWebviewMessage with unrecognized message type throws exception`() {
        // Setup
        val unrecognizedMessage = "{\"type\": \"javascript is for clowns\", \"data\": {}}"

        // Act & Assert
        assertThrows(IllegalStateException::class.java) {
            decodeWebviewMessage(unrecognizedMessage)
        }
    }

    @Test
    fun `test decodeWebviewMessage properly decodes show type`() {
        // Setup
        val showMessage = "{\"type\": \"show\", \"data\": {}}"

        // Act
        val result = decodeWebviewMessage(showMessage)

        // Assert
        assertEquals(KlaviyoWebFormMessageType.Show, result)
    }

    @Test
    fun `test decodeWebviewMessage properly decodes close type`() {
        // Setup
        val closeMessage = "{\"type\": \"close\", \"data\": {}}"

        // Act
        val result = decodeWebviewMessage(closeMessage)

        // Assert
        assertEquals(KlaviyoWebFormMessageType.Close, result)
    }

    @Test
    fun `test decodeWebviewMessage properly decodes console type`() {
        // Setup
        val consoleMessage = """
            {
              "type": "console",
              "data": {
                "message": {"text": "Test message"},
                "level": "info"
              }
            }
        """.trimIndent()

        // Act
        val result = decodeWebviewMessage(consoleMessage) as KlaviyoWebFormMessageType.Console

        // Assert
        assertEquals("{\"text\":\"Test message\"}", result.consoleLog)
        assertEquals("info", result.level)
    }

    @Test
    fun `test decodeWebviewMessage errors when profile event has no event name`() {
        // Setup
        val eventMessage = "{\"type\": \"profileEvent\", \"data\": {\"properties\": {}}}"
        every { Registry.log.error(any(), any<Throwable>()) } just Runs

        // Act & Assert
        assertThrows(IllegalStateException::class.java) {
            decodeWebviewMessage(eventMessage)
        }
    }
}