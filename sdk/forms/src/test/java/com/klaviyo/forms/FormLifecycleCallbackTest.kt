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

        every { mockPresentationManager.currentFormId } returns testFormId

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form dismissed message from webview
        val message = """{"type":"formDisappeared"}"""
        nativeBridge.postMessage(message)

        assertEquals(FormLifecycleEvent.FORM_DISMISSED, capturedEvent)
        assertEquals(testFormId, capturedFormId)
    }

    @Test
    fun `FORM_CTA_CLICKED event is triggered when deep link is opened`() {
        var capturedEvent: FormLifecycleEvent? = null
        var capturedFormId: FormId? = null
        val callback = FormLifecycleCallback { event, formId ->
            capturedEvent = event
            capturedFormId = formId
        }

        every { mockPresentationManager.currentFormId } returns testFormId
        mockkObject(DeepLinking)
        every { DeepLinking.handleDeepLink(any()) } returns Unit

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate deep link message from webview
        val message = """{"type":"openDeepLink", "data":{"android":"https://example.com"}}"""
        nativeBridge.postMessage(message)

        assertEquals(FormLifecycleEvent.FORM_CTA_CLICKED, capturedEvent)
        assertEquals(testFormId, capturedFormId)
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
    fun `callback receives null formId when form is dismissed without currentFormId`() {
        var capturedEvent: FormLifecycleEvent? = null
        var capturedFormId: FormId? = null
        val callback = FormLifecycleCallback { event, formId ->
            capturedEvent = event
            capturedFormId = formId
        }

        every { mockPresentationManager.currentFormId } returns null

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form dismissed message
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
