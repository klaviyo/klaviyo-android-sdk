package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.webview.JavaScriptEvaluator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class KlaviyoJsBridgeTest : BaseTest() {

    private val jsEvaluator = mockk<JavaScriptEvaluator>(relaxed = true)
    private val bridge = KlaviyoJsBridge()

    override fun setup() {
        super.setup()
        Registry.register<JavaScriptEvaluator>(jsEvaluator)
    }

    override fun cleanup() {
        super.cleanup()
        Registry.unregister<JavaScriptEvaluator>()
    }

    @Test
    fun `compileJson returns correct JSON for supported functions`() {
        val expectedJson = KlaviyoJsBridge().handshake.compileJson()
        val actualJson = """
           [{"type":"profileMutation","version":1},{"type":"lifecycleEvent","version":1},{"type":"closeForm","version":1},{"type":"profileEvent","version":1}]
        """.trimIndent()
        assertEquals(actualJson, expectedJson)
    }

    @Test
    fun `profileEvent calls JS evaluator with correct JS`() {
        val testEvent = Event(
            metric = "Fate Sealed",
            properties = mapOf(
                EventKey.CUSTOM("name") to "Anna Karenina",
                EventKey.CUSTOM("location") to "Saint Petersburg"
            )
        )

        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.profileEvent(testEvent)

        verify {
            jsEvaluator.evaluateJavascript(
                eq(
                    """window.profileEvent("Fate Sealed","{"name":"Anna Karenina","location":"Saint Petersburg"}")"""
                ),
                any()
            )
        }
    }

    @Test
    fun `profileMutation calls JS evaluator with correct JS`() {
        val profile = Profile(
            externalId = "extId",
            email = "kermit@muppets.com",
            phoneNumber = "+1234567890"
        ).setProperty("anonymous_id", "anonId")

        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.profileMutation(profile)

        verify {
            jsEvaluator.evaluateJavascript(
                eq(
                    """window.profileMutation("extId","kermit@muppets.com","+1234567890","anonId")"""
                ),
                any()
            )
        }
    }

    @Test
    fun `profileMutation calls JS evaluator with correct JS if identifiers are null`() {
        val profile = Profile()

        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.profileMutation(profile)

        verify {
            jsEvaluator.evaluateJavascript(
                eq("""window.profileMutation("","","","")"""),
                any()
            )
        }
    }

    @Test
    fun `If evaluator returns false, we log an error`() {
        val profile = Profile()

        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(false)
        }

        bridge.profileMutation(profile)

        verify { jsEvaluator.evaluateJavascript(any(), any()) }
        verify { spyLog.error("JS profileMutation evaluation failed") }
    }

    @Test
    fun `lifecycleEvent calls JS evaluator with correct JS`() {
        val type = JsBridge.LifecycleEventType.background
        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.lifecycleEvent(type)

        verify {
            jsEvaluator.evaluateJavascript(
                eq("""window.lifecycleEvent("background")"""),
                any()
            )
        }
    }

    @Test
    fun `openForm calls JS evaluator with correct JS`() {
        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.openForm("formId")

        verify {
            jsEvaluator.evaluateJavascript(
                eq("""window.openForm("formId")"""),
                any()
            )
        }
    }

    @Test
    fun `closeForm calls JS evaluator with correct JS`() {
        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.closeForm("formId")

        verify {
            jsEvaluator.evaluateJavascript(
                eq("""window.closeForm("formId")"""),
                any()
            )
        }
    }
}
