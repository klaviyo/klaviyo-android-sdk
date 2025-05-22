package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.webview.JavaScriptEvaluator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    fun `setProfile calls JS evaluator with correct JS`() {
        val profile = Profile(
            externalId = "extId",
            email = "kermit@muppets.com",
            phoneNumber = "+1234567890"
        ).setProperty("anonymous_id", "anonId")

        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.setProfile(profile)

        verify {
            jsEvaluator.evaluateJavascript(
                eq("window.setProfile(\"extId\",\"kermit@muppets.com\",\"+1234567890\",\"anonId\")"),
                any()
            )
        }
    }

    @Test
    fun `setProfile calls JS evaluator with correct JS if identifiers are null`() {
        val profile = Profile()

        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.setProfile(profile)

        verify {
            jsEvaluator.evaluateJavascript(
                eq("window.setProfile(\"\",\"\",\"\",\"\")"),
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

        bridge.setProfile(profile)

        verify { jsEvaluator.evaluateJavascript(any(), any()) }
        verify { spyLog.error("JS setProfile evaluation failed") }
    }

    @Test
    fun `dispatchLifecycleEvent calls JS evaluator with correct JS`() {
        val type = JsBridge.LifecycleEventType.background
        every { jsEvaluator.evaluateJavascript(any(), any()) } answers {
            secondArg<(Boolean) -> Unit>().invoke(true)
        }

        bridge.dispatchLifecycleEvent(type)

        verify {
            jsEvaluator.evaluateJavascript(
                eq("window.dispatchLifecycleEvent(\"background\")"),
                any()
            )
        }
    }
}
