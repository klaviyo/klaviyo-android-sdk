package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.bridge.JsBridgeObserverCollection
import com.klaviyo.forms.bridge.KlaviyoNativeBridge
import com.klaviyo.forms.bridge.NativeBridge
import com.klaviyo.forms.presentation.KlaviyoPresentationManager
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.webview.JavaScriptEvaluator
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class InAppFormsTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        mockkConstructor(KlaviyoPresentationManager::class)
        mockkConstructor(KlaviyoNativeBridge::class)
        mockkConstructor(KlaviyoWebViewClient::class).apply {
            every { anyConstructed<KlaviyoWebViewClient>().initializeWebView() } returns this
        }
    }

    @After
    override fun cleanup() {
        unmockkAll()
        Registry.unregister<PresentationManager>()
        Registry.unregister<NativeBridge>()
        Registry.unregister<WebViewClient>()
        Registry.unregister<JavaScriptEvaluator>()
        Registry.unregister<JsBridge>()
        Registry.unregister<JsBridgeObserverCollection>()
        super.cleanup()
    }

    @Test
    fun `registers required services`() {
        assert(!Registry.isRegistered<PresentationManager>())
        assert(!Registry.isRegistered<NativeBridge>())
        assert(!Registry.isRegistered<WebViewClient>())
        assert(!Registry.isRegistered<JavaScriptEvaluator>())
        assert(!Registry.isRegistered<JsBridge>())
        assert(!Registry.isRegistered<JsBridgeObserverCollection>())

        Klaviyo.registerForInAppForms()

        assertNotNull(Registry.get<PresentationManager>())
        assertNotNull(Registry.get<NativeBridge>())
        assertNotNull(Registry.get<WebViewClient>())
        assertNotNull(Registry.get<JavaScriptEvaluator>())
        assertNotNull(Registry.get<JsBridge>())
        assertNotNull(Registry.get<JsBridgeObserverCollection>())
    }

    @Test
    fun `registers required services once`() {
        val presenter: PresentationManager = mockk()
        val bridge: NativeBridge = mockk()
        val client: WebViewClient = mockk<WebViewClient>().apply {
            every { initializeWebView() } returns Unit
        }
        val jsBridge: JsBridge = mockk()
        val observerCollection: JsBridgeObserverCollection = mockk()

        Registry.register<PresentationManager>(presenter)
        Registry.register<NativeBridge>(bridge)
        Registry.register<WebViewClient>(client)
        Registry.register<JsBridge>(jsBridge)
        Registry.register<JsBridgeObserverCollection>(observerCollection)

        Klaviyo.registerForInAppForms()

        // It used our pre-registered services, and didn't overwrite them
        assertEquals(presenter, Registry.get<PresentationManager>())
        assertEquals(bridge, Registry.get<NativeBridge>())
        assertEquals(client, Registry.get<WebViewClient>())
        verify { client.initializeWebView() }
        assertEquals(jsBridge, Registry.get<JsBridge>())
        assertEquals(observerCollection, Registry.get<JsBridgeObserverCollection>())
    }

    @Test(expected = Test.None::class)
    fun `missing config exception doesn't ruin our freaking lives`() {
        val exception = MissingConfig()
        every { anyConstructed<KlaviyoWebViewClient>().initializeWebView() } throws exception
        Klaviyo.registerForInAppForms()
        verify { spyLog.error("Klaviyo SDK accessed before initializing", exception) }
    }
}
