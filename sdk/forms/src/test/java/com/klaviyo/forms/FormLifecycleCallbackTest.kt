package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.bridge.KlaviyoNativeBridge
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.presentation.PresentationState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
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

    private val testFormId = "test-form-123"
    private val testFormName = "Test Form"

    @Before
    override fun setup() {
        super.setup()
        every { mockPresentationManager.formContext } returns null
        every { mockPresentationManager.presentationState } returns PresentationState.Presented(
            "formId"
        )
        Registry.register<PresentationManager>(mockPresentationManager)
        nativeBridge = KlaviyoNativeBridge()
    }

    @After
    override fun cleanup() {
        unmockkAll()
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
    fun `FORM_SHOWN event is triggered by presentation manager, not bridge`() {
        var capturedEvent: FormLifecycleEvent? = null
        val callback = FormLifecycleCallback { event, _ ->
            capturedEvent = event
        }

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form shown message from webview — bridge delegates to present()
        val message = """{"type":"formWillAppear", "data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        nativeBridge.postMessage(message)

        // FORM_SHOWN is now fired by the presentation manager, not the bridge
        verify { mockPresentationManager.present(testFormId, testFormName) }
        assertEquals(null, capturedEvent)
    }

    @Test
    fun `FORM_DISMISSED event is triggered when form is dismissed`() {
        var capturedEvent: FormLifecycleEvent? = null
        var capturedContext: FormContext? = null
        val callback = FormLifecycleCallback { event, context ->
            capturedEvent = event
            capturedContext = context
        }

        every { mockPresentationManager.formContext } returns FormContext(testFormId, testFormName)
        Klaviyo.registerFormLifecycleCallback(callback)

        // First show the form so formContext is populated
        val showMessage = """{"type":"formWillAppear", "data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        nativeBridge.postMessage(showMessage)

        // Simulate form dismissed message from webview
        val message = """{"type":"formDisappeared","data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        assertEquals(FormLifecycleEvent.FORM_DISMISSED, capturedEvent)
        assertEquals(testFormId, capturedContext?.formId)
        assertEquals(testFormName, capturedContext?.formName)
    }

    @Test
    fun `FORM_CTA_CLICKED event is triggered when deep link is opened (v2 protocol)`() {
        // In v2, FormDisappeared is sent before OpenDeepLink, so the bridge must retain
        // the context from the show message to attach to subsequent events.
        val events = mutableListOf<Pair<FormLifecycleEvent, FormContext>>()
        val callback = FormLifecycleCallback { event, context -> events.add(event to context) }

        every { mockPresentationManager.formContext } returns FormContext(testFormId, testFormName)
        mockkObject(DeepLinking)
        every { DeepLinking.handleDeepLink(any()) } returns Unit

        Klaviyo.registerFormLifecycleCallback(callback)

        // First show the form so formContext is populated
        nativeBridge.postMessage(
            """{"type":"formWillAppear","data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        )
        // v2: FormDisappeared arrives next
        nativeBridge.postMessage(
            """{"type":"formDisappeared","data":{"formId":"$testFormId"}}"""
        )
        // Then OpenDeepLink
        nativeBridge.postMessage(
            """{"type":"openDeepLink","data":{"android":"https://example.com"}}"""
        )

        // FORM_SHOWN is now fired by the presentation manager, not the bridge
        assertEquals(2, events.size)
        assertEquals(FormLifecycleEvent.FORM_DISMISSED, events[0].first)
        assertEquals(testFormId, events[0].second.formId)
        assertEquals(FormLifecycleEvent.FORM_CTA_CLICKED, events[1].first)
        assertEquals(testFormId, events[1].second.formId)
    }

    @Test
    fun `callback is not invoked when no callback is registered`() {
        // Simulate form shown message without registering a callback
        val message = """{"type":"formWillAppear", "data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        // Verify PresentationManager was called but no exception thrown
        verify { mockPresentationManager.present(testFormId, any()) }
    }

    @Test
    fun `callback receives null formId when no form was shown prior to dismiss`() {
        var capturedEvent: FormLifecycleEvent? = null
        var capturedContext: FormContext? = null
        val callback = FormLifecycleCallback { event, context ->
            capturedEvent = event
            capturedContext = context
        }

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form dismissed message without a prior show (lastFormContext is null)
        val message = """{"type":"formDisappeared"}"""
        nativeBridge.postMessage(message)

        assertEquals(FormLifecycleEvent.FORM_DISMISSED, capturedEvent)
        assertEquals(null, capturedContext?.formId)
        assertEquals(null, capturedContext?.formName)
    }

    @Test
    fun `callback is invoked on UI thread`() {
        val callback = FormLifecycleCallback { _, _ -> }

        Klaviyo.registerFormLifecycleCallback(callback)

        // Simulate form dismissed message (bridge still fires FORM_DISMISSED)
        val message = """{"type":"formDisappeared","data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        // Verify threadHelper.runOnUiThread was called
        verify { mockThreadHelper.runOnUiThread(any()) }
    }
}
