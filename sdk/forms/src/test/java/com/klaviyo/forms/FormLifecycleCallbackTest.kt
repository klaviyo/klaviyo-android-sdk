package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.bridge.FormId
import com.klaviyo.forms.bridge.KlaviyoNativeBridge
import com.klaviyo.forms.presentation.PresentationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for form lifecycle callback functionality
 */
internal class FormLifecycleCallbackTest : BaseTest() {

    private val mockPresentationManager: PresentationManager = mockk(relaxed = true)
    private lateinit var nativeBridge: KlaviyoNativeBridge

    private val testFormId: FormId = "test-form-123"

    @Before
    override fun setup() {
        super.setup()
        Registry.register<PresentationManager>(mockPresentationManager)
        nativeBridge = KlaviyoNativeBridge()
    }

    @After
    override fun cleanup() {
        Registry.unregister<PresentationManager>()
        Registry.unregister<FormLifecycleCallback>()
        super.cleanup()
    }

    @Test
    fun `registerFormLifecycleCallback registers callback successfully`() {
        val callback = FormLifecycleCallback { _, _ -> }

        Klaviyo.registerFormLifecycleCallback(callback)

        assertEquals(callback, Registry.get<FormLifecycleCallback>())
    }

    @Test
    fun `unregisterFormLifecycleCallback removes callback`() {
        val callback = FormLifecycleCallback { _, _ -> }

        Klaviyo.registerFormLifecycleCallback(callback)

        Klaviyo.unregisterFormLifecycleCallback()

        assertEquals(null, Registry.getOrNull<FormLifecycleCallback>())
    }


    @Test
    fun `registerFormLifecycleCallback replaces existing callback`() {
        val firstCallback = FormLifecycleCallback { _, _ -> }
        val secondCallback = FormLifecycleCallback { _, _ -> }

        Klaviyo.registerFormLifecycleCallback(firstCallback)
        assertEquals(firstCallback, Registry.get<FormLifecycleCallback>())

        Klaviyo.registerFormLifecycleCallback(secondCallback)
        assertEquals(secondCallback, Registry.get<FormLifecycleCallback>())
    }

    @Test
    fun `FORM_SHOWN event is triggered when form is shown`() {
        var capturedEvent: FormLifecycleEvent? = null
        var capturedFormId: FormId? = null
        val callback = FormLifecycleCallback { event, formId ->
            capturedEvent = event
            capturedFormId = formId
        }

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form shown message from webview
        val message = """{"type":"formWillAppear", "data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        assertEquals(FormLifecycleEvent.FORM_SHOWN, capturedEvent)
        assertEquals(testFormId, capturedFormId)
    }

    @Test
    fun `FORM_DISMISSED event is triggered when form is dismissed`() {
        var capturedEvent: FormLifecycleEvent? = null
        var capturedFormId: FormId? = null
        val callback = FormLifecycleCallback { event, formId ->
            capturedEvent = event
            capturedFormId = formId
        }

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form dismissed message from webview, formId comes from the bridge message
        val message = """{"type":"formDisappeared","data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        assertEquals(FormLifecycleEvent.FORM_DISMISSED, capturedEvent)
        assertEquals(testFormId, capturedFormId)
    }

    @Test
    fun `FORM_CTA_CLICKED event is triggered when deep link is opened (v2 protocol)`() {
        // In v2, FormDisappeared is sent before OpenDeepLink, so the bridge must retain
        // the formId from the dismiss message to attach to the CTA event.
        val events = mutableListOf<Pair<FormLifecycleEvent, FormId?>>()
        val callback = FormLifecycleCallback { event, formId -> events.add(event to formId) }

        mockkObject(DeepLinking)
        every { DeepLinking.handleDeepLink(any()) } returns Unit

        Klaviyo.registerFormLifecycleCallback(callback)

        // v2: FormDisappeared arrives first
        nativeBridge.postMessage("""{"type":"formDisappeared","data":{"formId":"$testFormId"}}""")
        // Then OpenDeepLink
        nativeBridge.postMessage("""{"type":"openDeepLink","data":{"android":"https://example.com"}}""")

        assertEquals(2, events.size)
        assertEquals(FormLifecycleEvent.FORM_DISMISSED to testFormId, events[0])
        assertEquals(FormLifecycleEvent.FORM_CTA_CLICKED to testFormId, events[1])
    }

    @Test
    fun `callback is not invoked when no callback is registered`() {
        // Simulate form shown message without registering a callback
        val message = """{"type":"formWillAppear", "data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        // Verify PresentationManager was called but no exception thrown
        verify { mockPresentationManager.present(testFormId) }
    }

    @Test
    fun `callback receives null formId when formDisappeared message has no formId`() {
        var capturedEvent: FormLifecycleEvent? = null
        var capturedFormId: FormId? = null
        val callback = FormLifecycleCallback { event, formId ->
            capturedEvent = event
            capturedFormId = formId
        }

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form dismissed message with no formId in payload
        val message = """{"type":"formDisappeared"}"""
        nativeBridge.postMessage(message)

        assertEquals(FormLifecycleEvent.FORM_DISMISSED, capturedEvent)
        assertEquals(null, capturedFormId)
    }

    @Test
    fun `callback is invoked on UI thread`() {
        val callback = FormLifecycleCallback { _, _ -> }

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form shown message
        val message = """{"type":"formWillAppear", "data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        // Verify threadHelper.runOnUiThread was called
        verify { mockThreadHelper.runOnUiThread(any()) }
    }
}
