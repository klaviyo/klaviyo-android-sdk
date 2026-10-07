package com.klaviyo.forms.webview

import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import androidx.core.view.ViewCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.AuthTokenProvider
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.core.config.KlaviyoConfig
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.forms.bridge.HandshakeSpec
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.bridge.JsBridgeObserverCollection
import com.klaviyo.forms.bridge.JwtObserver
import com.klaviyo.forms.bridge.NativeBridge
import com.klaviyo.forms.bridge.NativeBridgeMessage
import com.klaviyo.forms.bridge.compileJson
import com.klaviyo.forms.bridge.toBridgeJson
import com.klaviyo.forms.presentation.PresentationManager
import io.mockk.CapturingSlot
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KlaviyoWebViewClientTest : BaseTest() {

    companion object {
        private const val JWT = "header.payload.signature"
        private const val TOKEN_LATENCY_MS = 100L
        private const val LATE_TOKEN_MS = 300L

        val HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head data-sdk-name="SDK_NAME"
                  data-sdk-version="SDK_VERSION"
                  data-native-bridge-name="BRIDGE_NAME"
                  data-native-bridge-handshake='BRIDGE_HANDSHAKE'
                  data-forms-data-environment='FORMS_ENVIRONMENT'
                  data-klaviyo-local-tracking="1"
                  data-klaviyo-profile='KLAVIYO_PROFILE'
                  data-klaviyo-jwt='KLAVIYO_JWT'
            >
                <meta charset="UTF-8">
                <meta name="viewport"
                      content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0, viewport-fit=cover"/>

                <!--  This meta tag protects @imported fonts from being blocked by CORS  -->
                <meta name="referrer" content="same-origin"/>

                <title>Klaviyo In-App Form Template</title>

                <!-- Load in JS helper functions from assets directory -->
                <script type="text/javascript" src="file:///android_asset/onsite-bridge.js"></script>

                <!-- Static stylesheet for "websafe" fonts that may be unavailable or inconsistent from the system -->
                <link rel="stylesheet" type="text/css"
                      href="https://static-forms.klaviyo.com/fonts/api/v1/in-app-web-fonts/websafe_fonts.css" crossorigin/>

                <!-- Placeholder script to load klaviyo.js -->
                <script type="text/javascript" src="KLAVIYO_JS_URL"></script>
            </head>
            <body></body>
            </html>
        """.trimIndent()
    }

    private val stubKlaviyoJs = "stubKlaviyoJs"
    private val mockKlaviyoJsUri = mockk<Uri>(relaxed = true).also {
        every { it.toString() } returns stubKlaviyoJs
    }
    private val mockUriBuilder = mockk<Uri.Builder>(relaxed = true).also {
        every { it.build() } returns mockKlaviyoJsUri
        every { it.path("onsite/js/klaviyo.js") } returns it
        every { it.appendQueryParameter("company_id", any()) } returns it
        every { it.appendQueryParameter("env", any()) } returns it
    }
    private val mockCdnUri = mockk<Uri>(relaxed = true).also {
        every { it.buildUpon() } returns mockUriBuilder
    }

    private val mockBridge: NativeBridge = mockk<NativeBridge>(relaxed = true).apply {
        every { name } returns "MockNativeBridge"
        every { allowedOrigin } returns setOf(mockConfig.baseUrl)
        every { handshake } returns listOf(HandshakeSpec("mockNativeEvent", 1))
    }

    private val mockSettings: WebSettings = mockk(relaxed = true)
    private val mockParentView: ViewGroup = mockk(relaxed = true)
    private val mockAssets = mockk<AssetManager> {
        every { open("InAppFormsTemplate.html") } answers {
            ByteArrayInputStream(HTML_TEMPLATE.encodeToByteArray())
        }
    }

    private val mockJsBridge = mockk<JsBridge>(relaxed = true).apply {
        every { handshake } returns listOf(HandshakeSpec("mockObserver", 1))
    }

    private val mockObserverCollection = mockk<JsBridgeObserverCollection>(relaxed = true)
    private val fakeAuth = FakeAuthTokenManager()
    private val mockState = mockk<State>(relaxed = true).apply {
        every { getAsProfile() } returns Profile()
    }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<JsBridge>(mockJsBridge)
        Registry.register<JsBridgeObserverCollection>(mockObserverCollection)
        Registry.register<NativeBridge>(mockBridge)
        Registry.register<State>(mockState)
        Registry.register<AuthTokenManager>(fakeAuth)
        mockDeviceProperties()
        every { mockConfig.isDebugBuild } returns false
        every { mockContext.assets } returns mockAssets

        mockkStatic(ViewCompat::class)
        every { ViewCompat.setOnApplyWindowInsetsListener(any(), any()) } just runs

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

        val cdnStub = "https://decent.cdn.url.com"
        every { mockConfig.baseCdnUrl } returns cdnStub
        mockkStatic(Uri::class)
        every { Uri.parse(cdnStub) } returns mockCdnUri

        mockkStatic(WebViewCompat::class)
        every { WebViewCompat.addWebMessageListener(any(), any(), any(), any()) } just runs
        every { WebViewCompat.removeWebMessageListener(any(), any()) } just runs

        every { anyConstructed<KlaviyoWebView>().removeJavascriptInterface(any()) } just runs
    }

    @After
    override fun cleanup() {
        Registry.unregister<NativeBridge>()
        Registry.unregister<State>()
        Registry.unregister<JsBridgeObserverCollection>()
        Registry.unregister<AuthTokenManager>()
        clearAllMocks()
        super.cleanup()
    }

    private fun verifyClose(doesNotClose: Boolean = false) {
        val times = if (doesNotClose) 0 else 1
        verify(exactly = times) { anyConstructed<KlaviyoWebView>().visibility = View.GONE }
        verify(exactly = times) { mockParentView.removeView(any()) }
    }

    private fun verifyDestroy(doesNotDestroy: Boolean = false) {
        verify(inverse = doesNotDestroy) { spyLog.verbose("Clear IAF WebView reference") }
        verify(inverse = doesNotDestroy) { anyConstructed<KlaviyoWebView>().destroy() }
        verify(inverse = doesNotDestroy) { mockObserverCollection.stopObservers() }
    }

    private fun verifyShow(doesNotShow: Boolean = false) {
        val times = if (doesNotShow) 0 else 1
        verify(exactly = times) { anyConstructed<KlaviyoWebView>().visibility = View.VISIBLE }
        verify(exactly = times) { mockActivity.setContentView(any<KlaviyoWebView>()) }
    }

    @Test
    fun `initializeWebView triggers loadTemplate with proper template substitution`() {
        val expectedHandshake = listOf(
            HandshakeSpec("mockNativeEvent", 1),
            HandshakeSpec("mockObserver", 1)
        )

        val expectedHtml =
            """
            <!DOCTYPE html>
            <html lang="en">
            <head data-sdk-name="${mockConfig.sdkName}"
                  data-sdk-version="${mockConfig.sdkVersion}"
                  data-native-bridge-name="${mockBridge.name}"
                  data-native-bridge-handshake='${expectedHandshake.compileJson()}'
                  data-forms-data-environment='in-app'
                  data-klaviyo-local-tracking="1"
                  data-klaviyo-profile='${Profile().toBridgeJson()}'
                  data-klaviyo-jwt=''
            >
                <meta charset="UTF-8">
                <meta name="viewport"
                      content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0, viewport-fit=cover"/>
            
                <!--  This meta tag protects @imported fonts from being blocked by CORS  -->
                <meta name="referrer" content="same-origin"/>
            
                <title>Klaviyo In-App Form Template</title>
            
                <!-- Load in JS helper functions from assets directory -->
                <script type="text/javascript" src="file:///android_asset/onsite-bridge.js"></script>
            
                <!-- Static stylesheet for "websafe" fonts that may be unavailable or inconsistent from the system -->
                <link rel="stylesheet" type="text/css"
                      href="https://static-forms.klaviyo.com/fonts/api/v1/in-app-web-fonts/websafe_fonts.css" crossorigin/>
            
                <!-- Placeholder script to load klaviyo.js -->
                <script type="text/javascript" src="$stubKlaviyoJs"></script>
            </head>
            <body></body>
            </html>
            """.trimIndent()

        val html = captureTemplate()
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockAssets.open("InAppFormsTemplate.html") }
        verify { anyConstructed<KlaviyoWebView>().loadTemplate(any(), client, mockBridge) }
        assertEquals(expectedHtml, html.captured.decodeHtml())
        verify { mockConfig.sdkName }
        verify { mockConfig.sdkVersion }
        // tells us timer has started
        assertEquals(staticClock.scheduledTasks.size, 1)
    }

    private fun captureTemplate() = slot<String>().also { html ->
        every {
            anyConstructed<KlaviyoWebView>().loadTemplate(capture(html), any(), any())
        } just runs
    }

    private fun String.decodeHtml() = replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    @Test
    fun `initial document profile matches the profileMutation shape, escaped`() {
        val special = "O'Brien & <Admin> \"quoted\" DEVICE_INFO\n\t\u0001雪😀"
        listOf(
            Profile(),
            Profile().apply { anonymousId = "anon-id" },
            Profile(externalId = "ext", email = "a/b@example.com", phoneNumber = "+15555555555")
                .apply { anonymousId = "anon-id" },
            Profile(externalId = special, email = "mail+$special@example.com")
        ).forEach { profile ->
            every { mockState.getAsProfile() } returns profile
            val html = captureTemplate()
            KlaviyoWebViewClient().initializeWebView()
            dispatcher.scheduler.advanceUntilIdle()

            val encoded = requireNotNull(
                Regex("data-klaviyo-profile='([^']*)'").find(html.captured)?.groupValues?.get(1)
            )
            val rendered = JSONObject(encoded.decodeHtml())
            assertTrue(encoded.none { it in "<>\"'" })
            assertEquals(
                mapOf(
                    "external_id" to (profile.externalId ?: ""),
                    "email" to (profile.email ?: ""),
                    "phone_number" to (profile.phoneNumber ?: ""),
                    "anonymous_id" to (profile.anonymousId ?: "")
                ),
                rendered.keys().asSequence().associateWith { rendered.getString(it) }
            )
        }
    }

    /** Value of the data-klaviyo-jwt attribute in [html], or null if the page was not loaded. */
    private fun renderedJwt(html: CapturingSlot<String>): String? = if (html.isCaptured) {
        Regex("data-klaviyo-jwt='([^']*)'").find(html.captured)?.groupValues?.get(1)
    } else {
        null
    }

    @Test
    fun `page load waits for the initial JWT and seeds it as soon as it arrives`() {
        fakeAuth.providerRegistered = true
        val html = captureTemplate()
        KlaviyoWebViewClient().initializeWebView()

        dispatcher.scheduler.advanceTimeBy(TOKEN_LATENCY_MS)
        dispatcher.scheduler.runCurrent()
        assertNull("klaviyo.js must not load before the JWT", renderedJwt(html))
        assertEquals(0, staticClock.scheduledTasks.size)

        fakeAuth.completeFetch(JWT)
        dispatcher.scheduler.runCurrent()

        assertEquals(JWT, renderedJwt(html))
        assertEquals(TOKEN_LATENCY_MS, dispatcher.scheduler.currentTime)
        assertEquals(1, staticClock.scheduledTasks.size)
    }

    @Test
    fun `page loads without a JWT once the interactive budget expires`() {
        fakeAuth.providerRegistered = true
        val html = captureTemplate()
        KlaviyoWebViewClient().initializeWebView()

        dispatcher.scheduler.advanceTimeBy(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS - 1)
        dispatcher.scheduler.runCurrent()
        assertNull(renderedJwt(html))

        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()

        assertEquals("", renderedJwt(html))
        verify(exactly = 1) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
    }

    @Test
    fun `a JWT arriving after the budget is still delivered over the bridge`() {
        fakeAuth.providerRegistered = true
        val html = captureTemplate()
        val jwtObserver = JwtObserver()
        KlaviyoWebViewClient().initializeWebView()

        dispatcher.scheduler.advanceTimeBy(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
        dispatcher.scheduler.runCurrent()
        assertEquals("", renderedJwt(html))

        // Local JS ready: observers start against the fetch still in flight
        jwtObserver.startObserver()
        jwtObserver.publishProfile {}
        dispatcher.scheduler.advanceTimeBy(LATE_TOKEN_MS)
        fakeAuth.completeFetch(JWT)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockJsBridge.jwtMutation(JWT) }
        verify(exactly = 1) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
        jwtObserver.stopObserver()
    }

    @Test
    fun `page loads immediately without a JWT when auth is not enabled`() {
        val html = captureTemplate()
        KlaviyoWebViewClient().initializeWebView()
        dispatcher.scheduler.runCurrent()

        assertEquals("", renderedJwt(html))
        assertEquals(0L, dispatcher.scheduler.currentTime)
    }

    @Test
    fun `a JWT invalidated while awaited is not seeded`() {
        fakeAuth.providerRegistered = true
        val html = captureTemplate()
        KlaviyoWebViewClient().initializeWebView()

        fakeAuth.completeFetch(JWT)
        fakeAuth.current = null
        dispatcher.scheduler.runCurrent()

        assertEquals("", renderedJwt(html))
    }

    @Test
    fun `destroying the webview cancels a load awaiting the JWT`() {
        fakeAuth.providerRegistered = true
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.destroyWebView()

        fakeAuth.completeFetch(JWT)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
    }

    @Test
    fun `page loads without a JWT when the provider fails with a non-Exception throwable`() {
        fakeAuth.providerRegistered = true
        val html = captureTemplate()
        KlaviyoWebViewClient().initializeWebView()

        fakeAuth.failFetch(AssertionError("provider bug"))
        dispatcher.scheduler.runCurrent()

        assertEquals("", renderedJwt(html))
        verify { spyLog.warning(any(), any<AssertionError>()) }
    }

    @Test
    fun `a template load failure on the UI thread is contained and tears down the webview`() {
        // Release build: safeCall rethrows in debug builds to surface bugs during development
        mockkObject(KlaviyoConfig)
        every { KlaviyoConfig.isDebugBuild } returns false
        every {
            anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any())
        } throws IllegalStateException("WebView unavailable")
        KlaviyoWebViewClient().initializeWebView()
        dispatcher.scheduler.runCurrent()

        verify { spyLog.error(any(), any<IllegalStateException>()) }
        verifyDestroy()
        assertEquals(0, staticClock.scheduledTasks.size)
        unmockkObject(KlaviyoConfig)
    }

    @Test
    fun `destroying the webview after the load is queued on the UI thread skips the load`() {
        val uiQueue = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { uiQueue += firstArg<() -> Unit>() }
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        dispatcher.scheduler.runCurrent()
        assertEquals("load callback is queued", 1, uiQueue.size)

        client.destroyWebView()
        uiQueue.toList().forEach { it() }

        verify(exactly = 0) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
        verify { anyConstructed<KlaviyoWebView>().destroy() }
    }

    @Test
    fun `only initializes webview once`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.initializeWebView()
        dispatcher.scheduler.advanceUntilIdle()
        // Verify that loadTemplate was only called once (which means WebView was only constructed once)
        verify(exactly = 1) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
    }

    @Test
    fun `appends asset source`() {
        every { mockConfig.assetSource } returns "riders-on-the-stromboli"

        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        dispatcher.scheduler.advanceUntilIdle()

        verify {
            spyLog.debug(
                match { it.contains("assetSource") && it.contains("riders-on-the-stromboli") }
            )
        }
    }

    @Test
    fun `attachWebView causes webview to appear`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.attachWebView(mockActivity)
        verifyShow()
    }

    @Test
    fun `attachWebView with null webview does not display webview`() {
        val client = KlaviyoWebViewClient()
        // notably do not init webview
        client.attachWebView(mockActivity)
        verify { spyLog.warning(any()) }
        verifyShow(doesNotShow = true)
    }

    @Test
    fun `settings are properly set`() {
        assertEquals(false, mockSettings.javaScriptEnabled)
        assertEquals(false, mockSettings.domStorageEnabled)

        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockSettings.javaScriptEnabled = true }
        verify { mockSettings.userAgentString = "Mock User Agent" }
        verify { mockSettings.domStorageEnabled = true }
        verify(exactly = 0) { mockSettings.cacheMode }
    }

    @Test
    fun `attachesObservers when local JS initializes`() {
        KlaviyoWebViewClient().onLocalJsReady()
        verify { mockObserverCollection.startObservers(NativeBridgeMessage.JsReady) }
    }

    @Test
    fun `timeout cancels on handshake`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        dispatcher.scheduler.advanceUntilIdle()

        client.onJsHandshakeCompleted()
        staticClock.execute(10_000)
        client.onJsHandshakeCompleted() // verify a duplicate call wouldn't cause a crash

        verifyClose(doesNotClose = true)
        verifyDestroy(doesNotDestroy = true)

        verify { mockObserverCollection.startObservers(NativeBridgeMessage.HandShook) }
    }

    @Test
    fun `closes webview on timeout`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        dispatcher.scheduler.advanceUntilIdle()
        // notably no handshake
        staticClock.execute(10_000)

        verify { spyLog.warning(any()) }
        verifyDestroy()
    }

    @Test
    fun `detachWebView removes webview from view`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.detachWebView()

        verify { mockThreadHelper.runOnUiThread(any()) }

        verifyClose()
    }

    @Test
    fun `destroyWebView stops observers and kills webview on main thread`() {
        val client = KlaviyoWebViewClient()

        client.destroyWebView()
        verify(inverse = true) { anyConstructed<KlaviyoWebView>().destroy() }

        client.initializeWebView()
        client.destroyWebView()

        verify { mockObserverCollection.stopObservers() }
        verify { mockThreadHelper.runOnUiThread(any()) }

        verifyDestroy()
    }

    @Test
    fun `destroyWebView removes WebMessageListener when supported`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.destroyWebView()

        verify { WebViewCompat.removeWebMessageListener(any(), eq("MockNativeBridge")) }
    }

    @Test
    fun `destroyWebView removes JavascriptInterface when WebMessageListener unsupported`() {
        every { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) } returns false
        every { anyConstructed<KlaviyoWebView>().addJavascriptInterface(any(), any()) } just runs

        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        client.destroyWebView()

        verify { anyConstructed<KlaviyoWebView>().removeJavascriptInterface(eq("MockNativeBridge")) }
        verify(inverse = true) { WebViewCompat.removeWebMessageListener(any(), any()) }
    }

    @Test
    fun `verify detachWebView fails on a null webview`() {
        val client = KlaviyoWebViewClient()
        // notably do not init webview
        client.detachWebView()
        verify { spyLog.warning(any()) }
        verifyClose(doesNotClose = true)
        verifyDestroy(doesNotDestroy = true)
    }

    @Test
    fun `shouldOverrideUrlLoading redirects to external browser when isForMainFrame is true`() {
        val client = KlaviyoWebViewClient()
        val mockUrl = mockk<Uri>(relaxed = true)
        val mockRequest: WebResourceRequest = mockk {
            every { isForMainFrame } returns true
            every { url } returns mockUrl
        }

        every { mockContext.startActivity(any()) } just runs

        val mockIntent = MockIntent.setupIntentMocking()
        val result = client.shouldOverrideUrlLoading(null, mockRequest)

        assertEquals(true, result)
        assertEquals(Intent.ACTION_VIEW, mockIntent.action.captured)
        assertEquals(mockUrl, mockIntent.data.captured)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, mockIntent.flags.captured)
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

    @Test
    fun `evaluateJavascript invokes callback with false if webview is null`() {
        val client = KlaviyoWebViewClient()
        var result: Boolean? = null
        client.evaluateJavascript("test") { result = it }
        assertEquals(false, result)
    }

    @Test
    fun `evaluateJavascript invokes webview evaluateJavascript via runOnUiThread and calls back with true or false`() {
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        every { Registry.lifecycleMonitor.currentActivity } returns mockActivity

        // Simulate webview.evaluateJavascript returning "true"
        every { anyConstructed<KlaviyoWebView>().evaluateJavascript(any(), any()) } answers {
            val callback = secondArg<ValueCallback<String>>()
            callback.onReceiveValue("true")
        }
        var resultTrue: Boolean? = null
        client.evaluateJavascript("test") { resultTrue = it }
        assertEquals(true, resultTrue)

        // Simulate webview.evaluateJavascript returning "false"
        every { anyConstructed<KlaviyoWebView>().evaluateJavascript(any(), any()) } answers {
            val callback = secondArg<ValueCallback<String>>()
            callback.onReceiveValue("false")
        }

        var resultFalse: Boolean? = null
        client.evaluateJavascript("test") { resultFalse = it }
        assertEquals(false, resultFalse)
        verify(exactly = 2) { mockThreadHelper.runOnUiThread(any()) }
    }

    @Test
    fun `onRenderProcessGone handles webview crash`() {
        val mockPresentationManager = mockk<PresentationManager>(relaxed = true).apply {
            every { dismiss() } just runs
        }
        Registry.register<PresentationManager>(mockPresentationManager)
        val client = KlaviyoWebViewClient()
        val mockDetail: RenderProcessGoneDetail = mockk(relaxed = true)
        val result = client.onRenderProcessGone(null, mockDetail)

        assertEquals(true, result)
        verify { mockPresentationManager.dismiss() }
        verify { spyLog.error(any()) }
        Registry.unregister<PresentationManager>()
    }

    @Test
    fun `onReceivedHttpError logs a warning`() {
        val client = KlaviyoWebViewClient()
        val mockRequest = mockk<WebResourceRequest>(relaxed = true).apply {
            every { url.toString() } returns "https://example.com"
        }
        val mockResponse = mockk<WebResourceResponse>(relaxed = true).apply {
            every { statusCode } returns 404
        }
        client.onReceivedHttpError(null, mockRequest, mockResponse)
        verify { spyLog.warning(any()) }
    }

    @Test
    fun `onPageFinished logs the asset source`() {
        val mockWebview = mockk<KlaviyoWebView>(relaxed = true).apply {
            every { evaluateJavascript(any(), any()) } answers {
                val callback = secondArg<ValueCallback<String>>()
                callback.onReceiveValue("test-asset-source")
            }
        }
        every { mockConfig.assetSource } returns "test-asset-source"
        KlaviyoWebViewClient().onPageFinished(mockWebview, "https://example.com")
        verify { spyLog.debug(any()) }
    }
}

/**
 * Models the manager's single shared fetch: every [currentToken] caller awaits the same in-flight
 * provider call within its own timeout, and a caller timing out does not cancel it. Throws
 * [AuthTokenException.NoProviderRegistered] immediately unless [providerRegistered].
 */
private class FakeAuthTokenManager : AuthTokenManager {
    var providerRegistered = false
    var current: String? = null
    private val fetch = CompletableDeferred<ValidatedToken>()
    private val observers = mutableListOf<TokenRefreshObserver>()

    fun failFetch(error: Throwable) {
        fetch.completeExceptionally(error)
    }

    fun completeFetch(jwt: String) {
        current = jwt
        fetch.complete(ValidatedToken(jwt, 0L, 0L))
        observers.toList().forEach { it(jwt) }
    }

    override suspend fun currentToken(timeoutMs: Long): ValidatedToken {
        if (!providerRegistered) throw AuthTokenException.NoProviderRegistered
        return withTimeoutOrNull(timeoutMs) { fetch.await() } ?: throw AuthTokenException.TimedOut
    }

    override fun isCurrentToken(rawToken: String) = current == rawToken
    override fun onTokenRefresh(observer: TokenRefreshObserver) {
        observers += observer
    }
    override fun offTokenRefresh(observer: TokenRefreshObserver) {
        observers -= observer
    }
    override fun profileGeneration() = 0L
    override fun registerProvider(provider: AuthTokenProvider) = Unit
    override fun unregisterProvider() = Unit
    override fun refreshRejectedToken() = Unit
    override fun setIdentified(identified: Boolean) = Unit
    override fun invalidate() = 0L
    override suspend fun clearTokenState(expectedGeneration: Long) = Unit
}
