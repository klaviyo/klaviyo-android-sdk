package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class InAppFormsTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        mockkConstructor(KlaviyoWebViewClient::class)
    }

    @After
    override fun cleanup() {
        Registry.unregister<KlaviyoWebViewClient>()
        super.cleanup()
    }

    @Test
    fun `initializes with pre-registered delegate`() {
        val delegate: KlaviyoWebViewClient = mockk(relaxed = true)
        Registry.register<KlaviyoWebViewClient>(delegate)
        Klaviyo.registerForInAppForms()

        verify { delegate.initializeWebView() }
    }

    @Test
    fun `registers a delegate if we don't have one`() {
        every { anyConstructed<KlaviyoWebViewClient>().initializeWebView() } returns mockk()
        assert(!Registry.isRegistered<KlaviyoWebViewClient>())
        Klaviyo.registerForInAppForms()
        assert(Registry.isRegistered<KlaviyoWebViewClient>())
    }

    @Test(expected = Test.None::class)
    fun `missing config exception doesn't ruin our freaking lives`() {
        val exception = MissingConfig()
        every { anyConstructed<KlaviyoWebViewClient>().initializeWebView() } throws exception
        Klaviyo.registerForInAppForms()
        verify { spyLog.error("Klaviyo SDK accessed before initializing", exception) }
    }
}
