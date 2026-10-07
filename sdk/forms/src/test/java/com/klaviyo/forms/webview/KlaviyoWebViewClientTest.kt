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
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.auth.ValidatedToken
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
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
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
    private val mockState = mockk<State>(relaxed = true).apply {
        every { getAsProfile() } returns Profile()
    }

    private val refreshObserver = slot<TokenRefreshObserver>()
    private val mockAuthTokenManager = mockk<AuthTokenManager>().apply {
        every { onTokenRefresh(capture(refreshObserver)) } just runs
        every { offTokenRefresh(any()) } just runs
        every { isCurrentToken(any()) } returns true
        every { profileGeneration() } returns 0L
        coEvery { currentToken(any()) } throws AuthTokenException.NoProviderRegistered
    }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<JsBridge>(mockJsBridge)
        Registry.register<JsBridgeObserverCollection>(mockObserverCollection)
        Registry.register<NativeBridge>(mockBridge)
        Registry.register<State>(mockState)
        Registry.register<AuthTokenManager>(mockAuthTokenManager)
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
        Registry.unregister<AuthTokenManager>()
        Registry.unregister<WebViewClient>()
        Registry.unregister<JsBridgeObserverCollection>()
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

    private fun String.documentJwt() =
        Regex("data-klaviyo-jwt='([^']*)'").find(this)?.groupValues?.get(1)

    /**
     * Resolve [AuthTokenManager.currentToken] with [token] once it completes, or time out after
     * the caller's budget in virtual time, as [com.klaviyo.core.auth.KlaviyoAuthTokenManager] does.
     */
    private fun tokenCompletesWith(token: CompletableDeferred<ValidatedToken>) {
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers {
            withTimeoutOrNull(firstArg<Long>()) { token.await() }
                ?: throw AuthTokenException.TimedOut
        }
    }

    private fun validatedToken(raw: String) = ValidatedToken(raw, 0L, 0L)

    @Test
    fun `template waits for a token arriving within the budget and seeds it in the document`() {
        val token = CompletableDeferred<ValidatedToken>()
        tokenCompletesWith(token)
        val html = captureTemplate()

        KlaviyoWebViewClient().initializeWebView()
        dispatcher.scheduler.advanceTimeBy(300)
        dispatcher.scheduler.runCurrent()

        verify(exactly = 0) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
        assertEquals(0, staticClock.scheduledTasks.size)

        token.complete(validatedToken("header.payload.sig"))
        dispatcher.scheduler.runCurrent()

        assertEquals(300L, dispatcher.scheduler.currentTime)
        verify(exactly = 1) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
        assertEquals("header.payload.sig", html.captured.documentJwt())
        coVerify {
            mockAuthTokenManager.currentToken(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
        }
        assertEquals(1, staticClock.scheduledTasks.size)
    }

    @Test
    fun `template loads without a token once the budget elapses, and the late token is injected`() {
        val token = CompletableDeferred<ValidatedToken>()
        tokenCompletesWith(token)
        val html = captureTemplate()
        val client = KlaviyoWebViewClient()
        Registry.register<WebViewClient>(client)

        client.initializeWebView()
        dispatcher.scheduler.advanceTimeBy(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS - 1)
        dispatcher.scheduler.runCurrent()
        verify(exactly = 0) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }

        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        verify(exactly = 1) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
        assertEquals("", html.captured.documentJwt())
        assertNull(client.documentToken)

        // The page's JsReady starts the JWT observer, whose fetch also times out
        JwtObserver().apply {
            startObserver()
            publishProfile {}
        }
        dispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { mockJsBridge.jwtMutation(any()) }

        // The provider responds after the budget; the manager publishes it to refresh observers
        token.complete(validatedToken("late.payload.sig"))
        refreshObserver.captured.invoke("late.payload.sig")

        verify(exactly = 1) { mockJsBridge.jwtMutation("late.payload.sig") }
    }

    @Test
    fun `template loads without waiting when no token can be fetched`() {
        val html = captureTemplate()

        KlaviyoWebViewClient().initializeWebView()
        dispatcher.scheduler.runCurrent()

        assertEquals(0L, dispatcher.scheduler.currentTime)
        assertEquals("", html.captured.documentJwt())
    }

    @Test
    fun `token replaced during the wait is not seeded in the document`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("stale.jwt.sig")
        every { mockAuthTokenManager.isCurrentToken("stale.jwt.sig") } returns false
        val html = captureTemplate()
        val client = KlaviyoWebViewClient()

        client.initializeWebView()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("", html.captured.documentJwt())
        assertNull(client.documentToken)
    }

    @Test
    fun `token seeded in the document is not injected again when observers start`() {
        coEvery {
            mockAuthTokenManager.currentToken(any())
        } returns validatedToken("seeded.jwt.sig")
        captureTemplate()
        val client = KlaviyoWebViewClient()
        Registry.register<WebViewClient>(client)

        client.initializeWebView()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("seeded.jwt.sig", client.documentToken)

        val jwtObserver = JwtObserver().apply {
            startObserver()
            publishProfile {}
        }
        dispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { mockJsBridge.jwtMutation(any()) }

        // A refetch writes its token even if it matches the seeded one
        jwtObserver.refetchToken()
        dispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { mockJsBridge.jwtMutation("seeded.jwt.sig") }
    }

    @Test
    fun `webview destroyed while awaiting the token never loads`() {
        val token = CompletableDeferred<ValidatedToken>()
        tokenCompletesWith(token)
        val client = KlaviyoWebViewClient()

        client.initializeWebView()
        dispatcher.scheduler.advanceTimeBy(100)
        client.destroyWebView()
        token.complete(validatedToken("header.payload.sig"))
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
        assertEquals(0, staticClock.scheduledTasks.size)
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
