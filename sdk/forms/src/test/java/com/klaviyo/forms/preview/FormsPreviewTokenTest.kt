package com.klaviyo.forms.preview

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.FormsProvider
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.KlaviyoForms
import com.klaviyo.forms.KlaviyoFormsProvider
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.bridge.JsBridgeObserverCollection
import com.klaviyo.forms.bridge.NativeBridge
import com.klaviyo.forms.endFormsPreview
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.reInitializeInAppForms
import com.klaviyo.forms.registerForInAppForms
import com.klaviyo.forms.startFormsPreview
import com.klaviyo.forms.unregisterFromInAppForms
import com.klaviyo.forms.webview.JavaScriptEvaluator
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Covers starting and ending a forms preview session through [KlaviyoFormsProvider]
 */
class FormsPreviewTokenTest : BaseTest() {

    private val mockClient = mockk<WebViewClient>(relaxed = true)
    private val mockPresentationManager = mockk<PresentationManager>(relaxed = true)

    private val storedToken: String?
        get() = Registry.getOrNull<FormsPreviewTokenStore>()?.token

    @Before
    override fun setup() {
        super.setup()
        Registry.register<FormsProvider>(KlaviyoFormsProvider())
        Registry.register<WebViewClient>(mockClient)
        Registry.register<JsBridge>(mockk<JsBridge>(relaxed = true))
        Registry.register<PresentationManager>(mockPresentationManager)
        Registry.register<NativeBridge>(mockk<NativeBridge>(relaxed = true))
        Registry.register<JsBridgeObserverCollection>(
            mockk<JsBridgeObserverCollection>(relaxed = true)
        )
    }

    @After
    override fun cleanup() {
        Registry.unregister<FormsProvider>()
        Registry.unregister<FormsPreviewTokenStore>()
        Registry.unregister<InAppFormsConfig>()
        Registry.unregister<WebViewClient>()
        Registry.unregister<JavaScriptEvaluator>()
        Registry.unregister<JsBridge>()
        Registry.unregister<PresentationManager>()
        Registry.unregister<NativeBridge>()
        Registry.unregister<JsBridgeObserverCollection>()
        super.cleanup()
    }

    @Test
    fun `preview started before register waits for register and is used by the first session`() {
        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)

        assertEquals(VALID_PREVIEW_TOKEN, storedToken)
        verify(exactly = 0) { mockClient.initializeWebView() }

        every { mockClient.initializeWebView() } answers {
            assertEquals(VALID_PREVIEW_TOKEN, storedToken)
        }
        Klaviyo.registerForInAppForms()

        verify(exactly = 1) { mockClient.initializeWebView() }
        verify(exactly = 0) { mockClient.destroyWebView() }
    }

    @Test
    fun `starting a preview after register starts a new session`() {
        Klaviyo.registerForInAppForms()

        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)

        assertEquals(VALID_PREVIEW_TOKEN, storedToken)
        verify(exactly = 1) { mockPresentationManager.dismiss() }
        verify(exactly = 1) { mockClient.destroyWebView() }
        verify(exactly = 2) { mockClient.initializeWebView() }
    }

    @Test
    fun `starting the same preview again restarts the session`() {
        Klaviyo.registerForInAppForms()

        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)
        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)

        verify(exactly = 2) { mockClient.destroyWebView() }
        verify(exactly = 3) { mockClient.initializeWebView() }
    }

    @Test
    fun `ending a preview starts a new session with published forms`() {
        Klaviyo.registerForInAppForms()
        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)

        Klaviyo.endFormsPreview()

        assertNull(storedToken)
        verify(exactly = 2) { mockClient.destroyWebView() }
        verify(exactly = 3) { mockClient.initializeWebView() }
    }

    @Test
    fun `ending a preview before register only clears the token`() {
        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)

        Klaviyo.endFormsPreview()

        assertNull(storedToken)
        verify(exactly = 0) { mockClient.initializeWebView() }
    }

    @Test
    fun `a malformed token changes nothing`() {
        Klaviyo.registerForInAppForms()
        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)

        Klaviyo.startFormsPreview("not-a-preview-token")

        assertEquals(VALID_PREVIEW_TOKEN, storedToken)
        verify(exactly = 1) { mockClient.destroyWebView() }
        verify { spyLog.warning(not(match { it.contains("not-a-preview-token") }), null) }
    }

    @Test
    fun `unregister ends the preview and a preview after unregister waits for register`() {
        Klaviyo.registerForInAppForms()
        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)

        Klaviyo.unregisterFromInAppForms()
        assertNull(storedToken)

        Klaviyo.startFormsPreview(OTHER_PREVIEW_TOKEN)

        assertEquals(OTHER_PREVIEW_TOKEN, storedToken)
        verify(exactly = 2) { mockClient.destroyWebView() }
        verify(exactly = 2) { mockClient.initializeWebView() }
    }

    @Test
    fun `registering again and reinitializing keep the preview`() {
        Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)
        Klaviyo.registerForInAppForms()
        Klaviyo.registerForInAppForms(InAppFormsConfig(30))
        Klaviyo.reInitializeInAppForms()

        assertEquals(VALID_PREVIEW_TOKEN, storedToken)
    }

    @Test
    fun `Java methods delegate to the provider`() {
        KlaviyoForms.startFormsPreview(VALID_PREVIEW_TOKEN)
        assertEquals(VALID_PREVIEW_TOKEN, storedToken)

        KlaviyoForms.endFormsPreview()
        assertNull(storedToken)
    }

    @Test
    fun `start and end throw when the forms module is missing`() {
        Registry.unregister<FormsProvider>()

        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.startFormsPreview(VALID_PREVIEW_TOKEN)
        }
        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.endFormsPreview()
        }
    }
}
