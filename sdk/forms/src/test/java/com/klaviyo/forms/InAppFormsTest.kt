package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.bridge.BridgeMessageHandler
import com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler
import com.klaviyo.forms.presentation.KlaviyoPresentationManager
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class InAppFormsTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        mockkConstructor(KlaviyoPresentationManager::class)
        mockkConstructor(KlaviyoBridgeMessageHandler::class)
        mockkConstructor(KlaviyoWebViewClient::class).apply {
            every { anyConstructed<KlaviyoWebViewClient>().initializeWebView() } returns this
        }
    }

    @After
    override fun cleanup() {
        Registry.unregister<PresentationManager>()
        Registry.unregister<BridgeMessageHandler>()
        Registry.unregister<WebViewClient>()
        super.cleanup()
    }

    @Test
    fun `registers required services`() {
        assert(!Registry.isRegistered<PresentationManager>())
        assert(!Registry.isRegistered<BridgeMessageHandler>())
        assert(!Registry.isRegistered<WebViewClient>())

        Klaviyo.registerForInAppForms()

        assert(Registry.isRegistered<PresentationManager>())
        assert(Registry.isRegistered<BridgeMessageHandler>())
        assert(Registry.isRegistered<WebViewClient>())
    }

    @Test
    fun `registers required services once`() {
        val presenter: PresentationManager = mockk()
        val bridge: BridgeMessageHandler = mockk()
        val client: WebViewClient = mockk<WebViewClient>().apply {
            every { initializeWebView() } returns Unit
        }
        Registry.register<PresentationManager>(presenter)
        Registry.register<BridgeMessageHandler>(bridge)
        Registry.register<WebViewClient>(client)

        Klaviyo.registerForInAppForms()

        // It used our pre-registered services, and didn't overwrite them
        assertEquals(presenter, Registry.get<PresentationManager>())
        assertEquals(bridge, Registry.get<BridgeMessageHandler>())
        assertEquals(client, Registry.get<WebViewClient>())
        verify { client.initializeWebView() }
    }

    @Test(expected = Test.None::class)
    fun `missing config exception doesn't ruin our freaking lives`() {
        val exception = MissingConfig()
        every { anyConstructed<KlaviyoWebViewClient>().initializeWebView() } throws exception
        Klaviyo.registerForInAppForms()
        verify { spyLog.error("Klaviyo SDK accessed before initializing", exception) }
    }
}
