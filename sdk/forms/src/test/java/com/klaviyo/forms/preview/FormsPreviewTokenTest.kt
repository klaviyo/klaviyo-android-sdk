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
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.reInitializeInAppForms
import com.klaviyo.forms.registerForInAppForms
import com.klaviyo.forms.setFormsPreviewToken
import com.klaviyo.forms.unregisterFromInAppForms
import com.klaviyo.forms.webview.JavaScriptEvaluator
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Covers the public preview token setter and config plumbing through [KlaviyoFormsProvider]
 */
class FormsPreviewTokenTest : BaseTest() {

    private val mockClient = mockk<WebViewClient>(relaxed = true)
    private val mockJsBridge = mockk<JsBridge>(relaxed = true)

    private val storedToken: String?
        get() = Registry.get<FormsPreviewTokenStore>().token

    @Before
    override fun setup() {
        super.setup()
        Registry.register<FormsProvider>(KlaviyoFormsProvider())
        Registry.register<WebViewClient>(mockClient)
        Registry.register<JsBridge>(mockJsBridge)
        Registry.register<PresentationManager>(mockk<PresentationManager>(relaxed = true))
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
    fun `config token is stored before the webview initializes`() {
        every { mockClient.initializeWebView() } answers {
            assertEquals(VALID_PREVIEW_TOKEN, storedToken)
        }

        Klaviyo.registerForInAppForms(InAppFormsConfig(previewToken = VALID_PREVIEW_TOKEN))

        verify(exactly = 1) { mockClient.initializeWebView() }
        assertEquals(VALID_PREVIEW_TOKEN, storedToken)
    }

    @Test
    fun `config without a token leaves a token set earlier`() {
        Klaviyo.setFormsPreviewToken(VALID_PREVIEW_TOKEN)
        Klaviyo.registerForInAppForms(InAppFormsConfig())

        assertEquals(VALID_PREVIEW_TOKEN, storedToken)
    }

    @Test
    fun `setter before register is used by the webview`() {
        Klaviyo.setFormsPreviewToken(VALID_PREVIEW_TOKEN)
        every { mockClient.initializeWebView() } answers {
            assertEquals(VALID_PREVIEW_TOKEN, storedToken)
        }

        Klaviyo.registerForInAppForms()

        verify(exactly = 1) { mockClient.initializeWebView() }
    }

    @Test
    fun `setter after register updates the token without re-initializing`() {
        Klaviyo.registerForInAppForms()
        Klaviyo.setFormsPreviewToken(VALID_PREVIEW_TOKEN)
        assertEquals(VALID_PREVIEW_TOKEN, storedToken)

        Klaviyo.setFormsPreviewToken(null)
        assertNull(storedToken)

        verify(exactly = 1) { mockClient.initializeWebView() }
        verify(exactly = 0) { mockClient.destroyWebView() }
    }

    @Test
    fun `re-registering with a new config token updates the stored token`() {
        val changes = mutableListOf<String?>()
        Klaviyo.registerForInAppForms(InAppFormsConfig(previewToken = VALID_PREVIEW_TOKEN))
        Registry.get<FormsPreviewTokenStore>().onTokenChange {
            changes += storedToken
        }

        Klaviyo.registerForInAppForms(InAppFormsConfig(previewToken = OTHER_PREVIEW_TOKEN))

        assertEquals(listOf<String?>(OTHER_PREVIEW_TOKEN), changes)
        verifyOrder {
            mockClient.initializeWebView()
            mockClient.initializeWebView()
        }
    }

    @Test
    fun `invalid token from the setter clears the previous token`() {
        Klaviyo.setFormsPreviewToken(VALID_PREVIEW_TOKEN)
        Klaviyo.setFormsPreviewToken("not-a-preview-token")

        assertNull(storedToken)
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `invalid token from config is dropped`() {
        Klaviyo.registerForInAppForms(InAppFormsConfig(previewToken = "<script>"))

        assertNull(storedToken)
        verify(exactly = 1) { mockClient.initializeWebView() }
    }

    @Test
    fun `reinitialize does not revive a preview that ended`() {
        Klaviyo.registerForInAppForms(InAppFormsConfig(previewToken = VALID_PREVIEW_TOKEN))
        Registry.get<FormsPreviewTokenStore>().clear()

        Klaviyo.reInitializeInAppForms()

        assertNull(storedToken)
    }

    @Test
    fun `re-registering with a config whose preview ended does not revive it`() {
        val config = InAppFormsConfig(previewToken = VALID_PREVIEW_TOKEN)
        Klaviyo.registerForInAppForms(config)
        Registry.get<FormsPreviewTokenStore>().end()

        Klaviyo.unregisterFromInAppForms()
        Klaviyo.registerForInAppForms(config)

        assertNull(storedToken)
    }

    @Test
    fun `a new config token still starts a preview after an earlier one ended`() {
        Klaviyo.registerForInAppForms(InAppFormsConfig(previewToken = VALID_PREVIEW_TOKEN))
        Registry.get<FormsPreviewTokenStore>().end()

        Klaviyo.registerForInAppForms(InAppFormsConfig(previewToken = OTHER_PREVIEW_TOKEN))

        assertEquals(OTHER_PREVIEW_TOKEN, storedToken)
    }

    @Test
    fun `the setter can restart a preview that ended`() {
        Klaviyo.registerForInAppForms(InAppFormsConfig(previewToken = VALID_PREVIEW_TOKEN))
        Registry.get<FormsPreviewTokenStore>().end()

        Klaviyo.setFormsPreviewToken(VALID_PREVIEW_TOKEN)

        assertEquals(VALID_PREVIEW_TOKEN, storedToken)
    }

    @Test
    fun `Java setter delegates to the provider`() {
        KlaviyoForms.setFormsPreviewToken(VALID_PREVIEW_TOKEN)
        assertEquals(VALID_PREVIEW_TOKEN, storedToken)

        KlaviyoForms.setFormsPreviewToken(null)
        assertNull(storedToken)
    }

    @Test
    fun `setter throws when the forms module is missing`() {
        Registry.unregister<FormsProvider>()

        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.setFormsPreviewToken(VALID_PREVIEW_TOKEN)
        }
    }
}
