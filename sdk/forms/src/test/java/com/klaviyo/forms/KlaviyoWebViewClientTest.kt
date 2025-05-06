package com.klaviyo.forms

import android.app.Activity
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.io.ByteArrayInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class KlaviyoWebViewClientTest : BaseTest() {

    companion object {
        private val slotOnActivityEvent = slot<ActivityObserver>()

        val HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head data-sdk-name="SDK_NAME"
                  data-sdk-version="SDK_VERSION"
                  data-native-bridge-name="BRIDGE_NAME"
                  data-native-bridge-handshake='BRIDGE_HANDSHAKE'
            >
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0, viewport-fit=cover"/>
                <meta name="referrer" content="same-origin" /> <!--  This meta tag protects @imported fonts from being blocked by CORS  -->
                <title>Klaviyo In-App Form Template</title>
                <link rel="stylesheet" type="text/css" href="https://static-forms.klaviyo.com/fonts/api/v1/in-app-web-fonts/websafe_fonts.css" crossorigin/>
                <script type="text/javascript" src="KLAVIYO_JS_URL"></script>
            </head>
            <body></body>
            </html>
        """.trimIndent()
    }

    private val mockSettings: WebSettings = mockk(relaxed = true)
    private val mockParentView: ViewGroup = mockk(relaxed = true)
    private val mockAssets = mockk<AssetManager> {
        every { open("InAppFormsTemplate.html") } returns
            ByteArrayInputStream(HTML.encodeToByteArray())
    }

    private val mockContentView: ViewGroup = mockk(relaxed = true)
    private val mockDecorView: View = mockk(relaxed = true) {
        every { findViewById<ViewGroup>(any()) } returns mockContentView
    }
    private val mockActivity: Activity = mockk(relaxed = true) {
        every { window.decorView } returns mockDecorView
    }

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
        every { mockConfig.isDebugBuild } returns false
        every { mockContext.assets } returns mockAssets
        every { mockLifecycleMonitor.currentActivity } returns mockActivity

        mockkConstructor(KlaviyoWebView::class)

        every { anyConstructed<KlaviyoWebView>().settings } returns mockSettings
        every { anyConstructed<KlaviyoWebView>().setBackgroundColor(any()) } just runs
        every { anyConstructed<KlaviyoWebView>().webViewClient = any() } just runs
        every { anyConstructed<KlaviyoWebView>().visibility = any() } just runs
        every { anyConstructed<KlaviyoWebView>().parent } returns mockParentView
        every { anyConstructed<KlaviyoWebView>().destroy() } just runs
        every {
            anyConstructed<KlaviyoWebView>().loadDataWithBaseURL(
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>()
            )
        } just runs

        mockkStatic(WebViewFeature::class)
        every { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) } returns true

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        mockkStatic(WebViewCompat::class)
        every { WebViewCompat.addWebMessageListener(any(), any(), any(), any()) } just runs

        every { mockLifecycleMonitor.onActivityEvent(capture(slotOnActivityEvent)) } just runs
    }

    @After
    override fun cleanup() {
        clearAllMocks()
        super.cleanup()
    }

    private fun verifyClose(doesNotClose: Boolean = false) {
        val times = if (doesNotClose) 0 else 1
        val slot = slot<Runnable>()
        verify(exactly = times) { mockDecorView.post(capture(slot)) }
        if (!doesNotClose) slot.captured.run()
        verify(exactly = times) { spyLog.verbose("Clear IAF WebView reference") }
        verify(exactly = times) { mockParentView.removeView(any()) }
        verify(exactly = times) { anyConstructed<KlaviyoWebView>().destroy() }
        assertEquals(staticClock.scheduledTasks.size, 0) // timer is cancelled
    }

    private fun verifyShow(doesNotShow: Boolean = false) {
        val times = if (doesNotShow) 0 else 1
        val slot = slot<Runnable>()
        verify(exactly = times) { mockDecorView.post(capture(slot)) }
        if (!doesNotShow) slot.captured.run()
        verify(exactly = times) { anyConstructed<KlaviyoWebView>().visibility = View.VISIBLE }
        verify(exactly = times) { mockDecorView.findViewById<ViewGroup>(any()) }
        verify(exactly = times) { mockContentView.addView(any()) }
    }

    @Test
    fun `initializeWebView triggers loadTemplate`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        every {
            anyConstructed<KlaviyoWebView>()
                .loadTemplate(any(), client)
        } returns Unit
        // checks we load and call these config values
        verify { anyConstructed<KlaviyoWebView>().loadTemplate(any(), client) }
        verify { mockAssets.open("InAppFormsTemplate.html") }
        verify { mockConfig.sdkName }
        verify { mockConfig.sdkVersion }
        // tells us timer has started
        assertEquals(staticClock.scheduledTasks.size, 1)
    }

    @Test
    fun `appends asset source`() {
        every { mockConfig.assetSource } returns "riders-on-the-stromboli"

        val client = KlaviyoWebViewClient()
        client.initializeWebView()

        verify { spyLog.debug("Appending assetSource=riders-on-the-stromboli to klaviyo.js") }
    }

    @Test
    fun `show causes webview to appear`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.show()
        verifyShow()
    }

    @Test
    fun `show with null webview does not display webview`() {
        val client = KlaviyoWebViewClient()
        // notably do not init webview
        client.show()
        verify { spyLog.warning("Unable to show IAF - null WebView reference") }
        verifyShow(doesNotShow = true)
    }

    @Test
    fun `show with null decorView does not display webview`() {
        every { mockActivity.window?.decorView } returns null

        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.show()

        verify { spyLog.warning("Unable to show IAF - null activity reference") }
        verifyShow(doesNotShow = true)
    }

    @Test
    fun `settings are properly set`() {
        assertEquals(false, mockSettings.javaScriptEnabled)
        assertEquals(false, mockSettings.domStorageEnabled)

        val client = KlaviyoWebViewClient()
        client.initializeWebView()

        verify { mockSettings.javaScriptEnabled = true }
        verify { mockSettings.userAgentString = "Mock User Agent" }
        verify { mockSettings.domStorageEnabled = true }
        verify(exactly = 0) { mockSettings.cacheMode }
    }

    @Test
    fun `timeout cancels on handshake`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()

        client.onJsHandshakeCompleted()
        staticClock.execute(10_000)

        verifyClose(doesNotClose = true)
    }

    @Test
    fun `closes webview on timeout`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        // notably no handshake
        staticClock.execute(10_000)

        verify { spyLog.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js") }
        verifyClose()
    }

    @Test
    fun `close removes webview from view`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.close()

        verifyClose()
    }

    @Test
    fun `verify webview closes on an orientation change`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()

        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(Configuration()))
        // if we emit the same config change we still should only close once
        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(Configuration()))

        verify(exactly = 1) { spyLog.debug("New screen orientation, closing form") }
        verifyClose()
    }

    @Test
    fun `verify close fails on a null webview`() {
        val client = KlaviyoWebViewClient()
        // notably do not init webview
        client.close()
        verify { spyLog.warning("Unable to close IAF - null WebView reference") }
        verifyClose(true)
    }

    @Test
    fun `verify close fails on a null decorView`() {
        every { mockActivity.window?.decorView } returns null

        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.close()
        verify { spyLog.warning("Unable to close IAF - null activity reference") }
        verifyClose(true)
    }

    @Test
    fun `shouldOverrideUrlLoading redirects to external browser when isForMainFrame is true`() {
        val client = KlaviyoWebViewClient()
        val mockUrl = mockk<Uri>(relaxed = true)
        val mockRequest: WebResourceRequest = mockk {
            every { isForMainFrame } returns true
            every { url } returns mockUrl
        }

        every { mockContext.startActivity(any(), null) } just runs

        val uriSlot = slot<Uri>()
        val actionSlot = slot<String>()
        val flagsSlot = slot<Int>()

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setData(capture(uriSlot)) } returns mockk<Intent>()
        every { anyConstructed<Intent>().setAction(capture(actionSlot)) } returns mockk<Intent>()
        every { anyConstructed<Intent>().setFlags(capture(flagsSlot)) } returns mockk<Intent>()

        val result = client.shouldOverrideUrlLoading(null, mockRequest)

        assertEquals(true, result)
        assertEquals(Intent.ACTION_VIEW, actionSlot.captured)
        assertEquals(mockUrl, uriSlot.captured)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flagsSlot.captured)
    }

    @Test
    fun `shouldOverrideUrlLoading does not redirect when isForMainFrame is false`() {
        val client = KlaviyoWebViewClient()
        val mockRequest: WebResourceRequest = mockk {
            every { isForMainFrame } returns false
        }

        val result = client.shouldOverrideUrlLoading(null, mockRequest)

        assertEquals(false, result)
    }
}
