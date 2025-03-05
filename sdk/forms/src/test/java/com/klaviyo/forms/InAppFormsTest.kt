package com.klaviyo.forms

import android.net.Uri
import android.webkit.WebSettings
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class InAppFormsTest : BaseTest() {

    private val mockSettings: WebSettings = mockk(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        mockkConstructor(KlaviyoWebView::class)

        every { anyConstructed<KlaviyoWebView>().settings } returns mockSettings
        every { anyConstructed<KlaviyoWebView>().setBackgroundColor(any()) } just runs
        every { mockConfig.isDebugBuild } returns false
        every { anyConstructed<KlaviyoWebView>().webViewClient = any() } just runs
        every {
            anyConstructed<KlaviyoWebView>().loadDataWithBaseURL(
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>()
            )
        } just runs
        mockkStatic(WebViewCompat::class)
        every { WebViewCompat.addWebMessageListener(any(), any(), any(), any()) } just runs
        mockkStatic(WebViewFeature::class)
        every { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) } returns true
    }

    @After
    fun clearMocks() {
        Registry.unregister<KlaviyoWebViewDelegate>()
    }

    @Test
    fun `initializes with pre registered delegate`() {
        val delegate: KlaviyoWebViewDelegate = mockk(relaxed = true)
        Registry.register<KlaviyoWebViewDelegate>(delegate)
        Klaviyo.registerForInAppForms()

        verify { delegate.initializeWebView() }
    }

    @Test
    fun `registers a delegate if we don't have one`() {
        assert(!Registry.isRegistered<KlaviyoWebViewDelegate>())
        Klaviyo.registerForInAppForms()
        assert(Registry.isRegistered<KlaviyoWebViewDelegate>())
    }
}
