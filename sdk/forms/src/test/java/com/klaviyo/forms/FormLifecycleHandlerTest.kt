package com.klaviyo.forms

import android.net.Uri
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
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for form lifecycle callback functionality
 */
internal class FormLifecycleHandlerTest : BaseTest() {

    private val mockPresentationManager: PresentationManager = mockk(relaxed = true)
    private val mockUri: Uri = mockk(relaxed = true)
    private lateinit var nativeBridge: KlaviyoNativeBridge

    private val testFormId = "test-form-123"
    private val testFormName = "Test Form"

    @Before
    override fun setup() {
        super.setup()
        every { mockPresentationManager.presentationState } returns PresentationState.Presented
        Registry.register<PresentationManager>(mockPresentationManager)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockUri
        nativeBridge = KlaviyoNativeBridge()
    }

    @After
    override fun cleanup() {
        unmockkAll()
        Registry.unregister<PresentationManager>()
        Registry.unregister<FormLifecycleHandler>()
        super.cleanup()
    }

    @Test
    fun `registerFormLifecycleHandler registers callback successfully`() {
        val callback = FormLifecycleHandler { _ -> }

        Klaviyo.registerFormLifecycleHandler(callback)

        assertEquals(callback, Registry.get<FormLifecycleHandler>())
    }

    @Test
    fun `unregisterFormLifecycleHandler removes callback`() {
        val callback = FormLifecycleHandler { _ -> }

        Klaviyo.registerFormLifecycleHandler(callback)

        Klaviyo.unregisterFormLifecycleHandler()

        assertEquals(null, Registry.getOrNull<FormLifecycleHandler>())
    }

    @Test
    fun `registerFormLifecycleHandler replaces existing callback`() {
        val firstCallback = FormLifecycleHandler { _ -> }
        val secondCallback = FormLifecycleHandler { _ -> }

        Klaviyo.registerFormLifecycleHandler(firstCallback)
        assertEquals(firstCallback, Registry.get<FormLifecycleHandler>())

        Klaviyo.registerFormLifecycleHandler(secondCallback)
        assertEquals(secondCallback, Registry.get<FormLifecycleHandler>())
    }

    @Test
    fun `FORM_SHOWN event is triggered by bridge on formWillAppear`() {
        var capturedEvent: FormLifecycleEvent? = null
        val callback = FormLifecycleHandler { event ->
            capturedEvent = event
        }

        Klaviyo.registerFormLifecycleHandler(callback)

        val message =
            """{"type":"formWillAppear", "data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        nativeBridge.postMessage(message)

        verify { mockPresentationManager.present() }
        val shownEvent = capturedEvent as FormLifecycleEvent.FormShown
        assertEquals(testFormId, shownEvent.formId)
        assertEquals(testFormName, shownEvent.formName)
    }

    @Test
    fun `formDisappeared delegates to presentation manager dismiss with formContext`() {
        Klaviyo.registerFormLifecycleHandler(FormLifecycleHandler { _ -> })

        val message =
            """{"type":"formDisappeared","data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        nativeBridge.postMessage(message)

        verify { mockPresentationManager.dismiss() }
    }

    @Test
    fun `FORM_CTA_CLICKED event is triggered when deep link is opened (v2 protocol)`() {
        // In v2, FormDisappeared is sent before OpenDeepLink, both now carry formId+formName
        val events = mutableListOf<FormLifecycleEvent>()
        val callback = FormLifecycleHandler { event -> events.add(event) }

        mockkObject(DeepLinking)
        every { DeepLinking.handleDeepLink(any()) } returns Unit

        Klaviyo.registerFormLifecycleHandler(callback)

        // First show the form
        nativeBridge.postMessage(
            """{"type":"formWillAppear","data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        )
        assertEquals(1, events.size)
        assertTrue(events[0] is FormLifecycleEvent.FormShown)

        // v2: FormDisappeared — bridge fires FormDismissed callback and delegates to PM
        nativeBridge.postMessage(
            """{"type":"formDisappeared","data":{"formId":"$testFormId","formName":"$testFormName"}}"""
        )
        verify { mockPresentationManager.dismiss() }
        assertEquals(2, events.size)
        assertTrue(events[1] is FormLifecycleEvent.FormDismissed)

        // Then OpenDeepLink — CTA callback fires directly from bridge
        nativeBridge.postMessage(
            """{"type":"openDeepLink","data":{"android":"https://example.com","formId":"$testFormId","formName":"$testFormName","buttonLabel":"Shop Now"}}"""
        )

        assertEquals(3, events.size)
        val ctaEvent = events[2] as FormLifecycleEvent.FormCtaClicked
        assertEquals(testFormId, ctaEvent.formId)
        assertEquals(testFormName, ctaEvent.formName)
        assertEquals("Shop Now", ctaEvent.buttonLabel)
        assertEquals(mockUri, ctaEvent.deepLinkUrl)
    }

    @Test
    fun `callback is not invoked when no callback is registered`() {
        // Simulate form shown message without registering a callback
        val message = """{"type":"formWillAppear", "data":{"formId":"$testFormId"}}"""
        nativeBridge.postMessage(message)

        // Verify PresentationManager was called but no exception thrown
        verify { mockPresentationManager.present() }
    }

    @Test
    fun `formDisappeared without data delegates dismiss with empty context fields`() {
        Klaviyo.registerFormLifecycleHandler(FormLifecycleHandler { _ -> })

        nativeBridge.postMessage("""{"type":"formDisappeared"}""")

        verify { mockPresentationManager.dismiss() }
    }

    @Test
    fun `CTA callback is dispatched to main thread`() {
        mockkObject(DeepLinking)
        every { DeepLinking.handleDeepLink(any()) } returns Unit

        Klaviyo.registerFormLifecycleHandler(FormLifecycleHandler { _ -> })

        nativeBridge.postMessage(
            """{"type":"openDeepLink","data":{"android":"https://example.com","formId":"$testFormId"}}"""
        )

        verify { mockThreadHelper.runOnUiThread(any()) }
    }
}
