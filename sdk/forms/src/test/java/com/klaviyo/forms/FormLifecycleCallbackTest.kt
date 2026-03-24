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
        every { mockPresentationManager.presentationState } returns PresentationState.Presented(
            FormContext("formId", null)
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
        verify { mockPresentationManager.present(FormContext(testFormId, testFormName)) }
        assertEquals(null, capturedEvent)
    }

    @Test
    fun `formDisappeared delegates to presentation manager dismiss with formContext`() {
        Klaviyo.registerFormLifecycleCallback(FormLifecycleCallback { _, _ -> })

        val message = """{"type":"formDisappeared","data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        nativeBridge.postMessage(message)

        // FORM_DISMISSED is now fired internally by PM's dismiss()
        verify { mockPresentationManager.dismiss(FormContext(testFormId, testFormName)) }
    }

    @Test
    fun `FORM_CTA_CLICKED event is triggered when deep link is opened (v2 protocol)`() {
        // In v2, FormDisappeared is sent before OpenDeepLink, both now carry formId+formName
        val events = mutableListOf<Pair<FormLifecycleEvent, FormContext>>()
        val callback = FormLifecycleCallback { event, context -> events.add(event to context) }

        mockkObject(DeepLinking)
        every { DeepLinking.handleDeepLink(any()) } returns Unit

        Klaviyo.registerFormLifecycleCallback(callback)

        // First show the form
        nativeBridge.postMessage(
            """{"type":"formWillAppear","data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        )
        // v2: FormDisappeared — bridge delegates to PM's dismiss()
        nativeBridge.postMessage(
            """{"type":"formDisappeared","data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        )
        verify { mockPresentationManager.dismiss(FormContext(testFormId, testFormName)) }

        // Then OpenDeepLink — CTA callback fires directly from bridge
        nativeBridge.postMessage(
            """{"type":"openDeepLink","data":{"android":"https://example.com","formId":"$testFormId","formName":"$testFormName"}}"""
        )

        // Only CTA fires directly from bridge; FORM_DISMISSED is now internal to PM
        assertEquals(1, events.size)
        assertEquals(FormLifecycleEvent.FORM_CTA_CLICKED, events[0].first)
        assertEquals(testFormId, events[0].second.formId)
        assertEquals(testFormName, events[0].second.formName)
    }

    @Test
    fun `callback is not invoked when no callback is registered`() {
        // Simulate form shown message without registering a callback
        val message = """{"type":"formWillAppear", "data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        // Verify PresentationManager was called but no exception thrown
        verify { mockPresentationManager.present(any()) }
    }

    @Test
    fun `formDisappeared without data delegates dismiss with null context fields`() {
        Klaviyo.registerFormLifecycleCallback(FormLifecycleCallback { _, _ -> })

        nativeBridge.postMessage("""{"type":"formDisappeared"}""")

        verify { mockPresentationManager.dismiss(FormContext(null, null)) }
    }

    @Test
    fun `CTA callback is invoked on UI thread`() {
        mockkObject(DeepLinking)
        every { DeepLinking.handleDeepLink(any()) } returns Unit

        Klaviyo.registerFormLifecycleCallback(FormLifecycleCallback { _, _ -> })

        // CTA callback still fires directly from the bridge
        nativeBridge.postMessage(
            """{"type":"openDeepLink","data":{"android":"https://example.com","formId":"$testFormId"}}"""
        )

        verify { mockThreadHelper.runOnUiThread(any()) }
    }
}
