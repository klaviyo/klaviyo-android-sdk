package com.klaviyo.forms.webview

import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.AuthTokenProvider
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.forms.FormsProvider
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.InAppFormsFailureHandler
import com.klaviyo.forms.KlaviyoFormsProvider
import com.klaviyo.forms.bridge.HandshakeSpec
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.bridge.JsBridgeObserverCollection
import com.klaviyo.forms.bridge.JwtObserver
import com.klaviyo.forms.bridge.KlaviyoNativeBridge
import com.klaviyo.forms.bridge.NativeBridge
import com.klaviyo.forms.bridge.NativeBridgeMessage
import com.klaviyo.forms.bridge.ProfileEventObserver
import com.klaviyo.forms.bridge.compileJson
import com.klaviyo.forms.bridge.toBridgeJson
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.reInitializeInAppForms
import io.mockk.CapturingSlot
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
import java.io.FileNotFoundException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        Registry.unregister<InAppFormsConfig>()
        Registry.unregister<WebViewClient>()
        Registry.unregister<PresentationManager>()
        Registry.unregister<FormsProvider>()
        clearAllMocks()
        super.cleanup()
    }

    private fun verifyClose(doesNotClose: Boolean = false) {
        val times = if (doesNotClose) 0 else 1
        verify(exactly = times) { anyConstructed<KlaviyoWebView>().visibility = View.GONE }
        verify(exactly = times) { mockParentView.removeView(any()) }
    }

    private fun registerClient(handler: InAppFormsFailureHandler): KlaviyoWebViewClient =
        registerClient(InAppFormsConfig(onFailure = handler))

    private fun registerClient(config: InAppFormsConfig): KlaviyoWebViewClient =
        KlaviyoWebViewClient().also { client ->
            Registry.register<WebViewClient>(client)
            Registry.register<PresentationManager>(mockk<PresentationManager>(relaxed = true))
            KlaviyoFormsProvider().register(config)
        }

    @Test
    fun `manual requests wait past handshake and only latest request drains once`() {
        val client = registerClient(InAppFormsConfig())
        client.openForm("first")
        client.onJsHandshakeCompleted()
        client.openForm("latest")
        verify(exactly = 0) { mockJsBridge.openForm(any()) }

        client.onFormsDataLoaded()
        client.onFormsDataLoaded()

        verify(exactly = 1) { mockJsBridge.openForm("latest") }
        verify(exactly = 0) { mockJsBridge.openForm("first") }
    }

    @Test
    fun `ready manual requests all forward while another form is visible`() {
        val client = registerClient(InAppFormsConfig())
        client.onFormsDataLoaded()
        val presenter = Registry.get<PresentationManager>()
        presenter.present("visible", null)

        client.openForm("same")
        client.openForm("same")
        client.openForm("other")

        verify(exactly = 2) { mockJsBridge.openForm("same") }
        verify(exactly = 1) { mockJsBridge.openForm("other") }
        verify(exactly = 1) { presenter.present(any(), any()) }
    }

    @Test
    fun `manual pending lifetime uses original arrival and strict captured window`() {
        val client = registerClient(InAppFormsConfig())
        client.openForm("expired")
        staticClock.time += 10_000
        KlaviyoFormsProvider().register(InAppFormsConfig(eventReplayWindow = 30.seconds))
        client.onFormsDataLoaded()
        verify(exactly = 0) { mockJsBridge.openForm(any()) }

        client.destroyWebView()
        KlaviyoFormsProvider().register(InAppFormsConfig())
        client.openForm("fresh")
        staticClock.time += 9_999
        client.onFormsDataLoaded()
        verify(exactly = 1) { mockJsBridge.openForm("fresh") }
    }

    @Test
    fun `manual pending window is capped at thirty seconds`() {
        val client = registerClient(InAppFormsConfig(eventReplayWindow = 60.seconds))
        client.openForm("expired")
        staticClock.time += 30_000
        client.onFormsDataLoaded()

        verify(exactly = 0) { mockJsBridge.openForm(any()) }
    }

    @Test
    fun `zero replay window skips waiting but ready requests still forward`() {
        val client = registerClient(InAppFormsConfig(eventReplayWindow = 0.seconds))
        client.openForm("loading")
        client.onFormsDataLoaded()
        client.openForm("ready")

        verify(exactly = 0) { mockJsBridge.openForm("loading") }
        verify(exactly = 1) { mockJsBridge.openForm("ready") }
    }

    @Test
    fun `blank manual IDs do not replace a pending request and nonblank IDs are preserved`() {
        val client = registerClient(InAppFormsConfig())
        val id = " form\"\n'\\id "
        client.openForm(id)
        client.openForm(" \n\t")
        client.onFormsDataLoaded()
        client.openForm("")

        verify(exactly = 1) { mockJsBridge.openForm(id) }
        verify(exactly = 1) { mockJsBridge.openForm(any()) }
    }

    @Test
    fun `manual requests before registration and after failure never initialize a view`() {
        val client = KlaviyoWebViewClient()
        client.openForm("before")
        assertNull(client.getWebView())
        Registry.register<WebViewClient>(client)
        Registry.register<PresentationManager>(mockk<PresentationManager>(relaxed = true))
        KlaviyoFormsProvider().register(InAppFormsConfig())
        client.openForm("pending")
        client.onJsAbort("failed")
        client.openForm("after")
        client.onFormsDataLoaded()

        assertNull(client.getWebView())
        verify(exactly = 0) { mockJsBridge.openForm(any()) }
    }

    @Test
    fun `unregister discards pending manual request and readiness for retry`() {
        val client = registerClient(InAppFormsConfig())
        client.openForm("retired")
        KlaviyoFormsProvider().unregister()
        KlaviyoFormsProvider().register(InAppFormsConfig())
        client.openForm("replacement")
        verify(exactly = 0) { mockJsBridge.openForm(any()) }
        client.onFormsDataLoaded()

        verify(exactly = 1) { mockJsBridge.openForm("replacement") }
        verify(exactly = 0) { mockJsBridge.openForm("retired") }
    }

    @Test
    fun `context replacement discards old manual request but accepts new context`() {
        var generation = 0L
        every { mockState.bufferedEventGeneration } answers { generation }
        val client = registerClient(InAppFormsConfig())
        client.openForm("old-profile")
        generation++
        client.onFormsDataLoaded()
        client.openForm("new-profile")

        verify(exactly = 0) { mockJsBridge.openForm("old-profile") }
        verify(exactly = 1) { mockJsBridge.openForm("new-profile") }
    }

    @Test
    fun `compatible profile enrichment preserves pending manual request`() {
        every { mockState.bufferedEventGeneration } returns 7L
        val client = registerClient(InAppFormsConfig())
        client.openForm("anonymous-request")
        every { mockState.getAsProfile() } returns Profile(email = EMAIL)
        client.onFormsDataLoaded()

        verify(exactly = 1) { mockJsBridge.openForm("anonymous-request") }
    }

    @Test
    fun `retired legacy and modern readiness cannot drain replacement manual request`() {
        val bridges = captureAttemptBridges()
        val client = registerClient(InAppFormsConfig())
        dispatcher.scheduler.runCurrent()
        val oldView = client.getWebView() as WebView
        val oldBridge = bridges.single()
        client.openForm("retired")
        client.destroyWebView()
        KlaviyoFormsProvider().register(InAppFormsConfig())
        dispatcher.scheduler.runCurrent()
        client.openForm("replacement")
        oldBridge.postMessage("""{"type":"formsDataLoaded"}""")
        oldBridge.onPostMessage(
            oldView,
            WebMessageCompat("""{"type":"formsDataLoaded"}""", null),
            mockk(),
            true,
            mockk()
        )
        verify(exactly = 0) { mockJsBridge.openForm(any()) }

        bridges.last().postMessage("""{"type":"formsDataLoaded"}""")
        bridges.last().onPostMessage(
            client.getWebView() as WebView,
            WebMessageCompat("""{"type":"formsDataLoaded"}""", null),
            mockk(),
            true,
            mockk()
        )
        verify(exactly = 1) { mockJsBridge.openForm("replacement") }
    }

    @Test
    fun `queued ready request cannot cross replacement view or profile`() {
        var generation = 0L
        every { mockState.bufferedEventGeneration } answers { generation }
        val client = registerClient(InAppFormsConfig())
        client.onFormsDataLoaded()
        val jobs = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { jobs += firstArg<() -> Unit>() }
        client.openForm("old-profile")
        generation++
        jobs.removeAt(0).invoke()
        client.openForm("old-view")
        every { mockThreadHelper.runOnUiThread(any()) } answers { firstArg<() -> Unit>().invoke() }
        client.destroyWebView()
        KlaviyoFormsProvider().register(InAppFormsConfig())
        client.onFormsDataLoaded()
        jobs.single().invoke()

        verify(exactly = 0) { mockJsBridge.openForm(any()) }
    }

    @Test
    fun `company change cannot dispatch into old runtime before rebuild`() {
        val client = registerClient(InAppFormsConfig())
        client.openForm("old-company")
        every { mockConfig.apiKey } returns "replacement-company"
        client.onFormsDataLoaded()
        client.openForm("new-company")

        verify(exactly = 0) { mockJsBridge.openForm(any()) }
    }

    @Test
    fun `company replaced during JWT wait uses loaded account for manual requests`() {
        var generation = 0L
        every { mockState.bufferedEventGeneration } answers { generation }
        fakeAuth.providerRegistered = true
        val client = registerClient(InAppFormsConfig())
        client.openForm("original-company")
        dispatcher.scheduler.runCurrent()
        verify(exactly = 0) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }

        every { mockConfig.apiKey } returns "replacement-company"
        generation++
        client.openForm("before-template")
        fakeAuth.completeFetch(JWT)
        dispatcher.scheduler.runCurrent()
        verify { mockUriBuilder.appendQueryParameter("company_id", "replacement-company") }
        client.onFormsDataLoaded()
        verify(exactly = 0) { mockJsBridge.openForm(any()) }

        client.openForm("loaded-company")

        verify(exactly = 1) { mockJsBridge.openForm("loaded-company") }
        verify(exactly = 1) { mockJsBridge.openForm(any()) }
    }

    @Test
    fun `failure tears down on main before handler can synchronously register again`() {
        var failures = 0
        var onMain = false
        every { mockThreadHelper.runOnUiThread(any()) } answers {
            val previous = onMain
            onMain = true
            try {
                firstArg<() -> Unit>().invoke()
            } finally {
                onMain = previous
            }
        }
        val client = KlaviyoWebViewClient()
        Registry.register<WebViewClient>(client)
        Registry.register<PresentationManager>(mockk<PresentationManager>(relaxed = true))
        KlaviyoFormsProvider().register(
            InAppFormsConfig(onFailure = {
                failures++
                assertTrue(onMain)
                assertNull(client.getWebView())
                assertTrue(staticClock.scheduledTasks.isEmpty())
                verify(exactly = 1) { mockObserverCollection.stopObservers() }
                verify(exactly = 1) { anyConstructed<KlaviyoWebView>().destroy() }
                KlaviyoFormsProvider().register(InAppFormsConfig())
            })
        )
        val oldView = client.getWebView()
        dispatcher.scheduler.runCurrent()
        staticClock.execute(1_000)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, failures)
        assertTrue(client.getWebView() != null)
        assertNotSame(oldView, client.getWebView())
        assertEquals(1, staticClock.scheduledTasks.size)
        verify(exactly = 1) { anyConstructed<KlaviyoWebView>().destroy() }
    }

    @Test
    fun `main frame failures notify once and stale errors timers and bridge messages cannot settle retry`() {
        val bridges = mutableListOf<NativeBridge>()
        every { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) } answers {
            bridges += thirdArg<NativeBridge>()
        }
        var failures = 0
        val client = registerClient {
            failures++
            KlaviyoFormsProvider().register(InAppFormsConfig(onFailure = { failures++ }))
        }
        every { mockBridge.postMessage("handshake") } answers { client.onJsHandshakeCompleted() }
        dispatcher.scheduler.runCurrent()
        val oldView = client.getWebView() as WebView
        val oldTimer = staticClock.scheduledTasks.single().task
        val mainRequest = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns true
        }
        client.onReceivedError(oldView, mainRequest, mockk<WebResourceError>(relaxed = true))
        dispatcher.scheduler.runCurrent()
        val replacement = client.getWebView()
        assertEquals(1, failures)
        assertNotSame(oldView, replacement)

        oldTimer()
        client.onReceivedHttpError(oldView, mainRequest, mockk(relaxed = true))
        client.onRenderProcessGone(oldView, mockk(relaxed = true))
        bridges.first().postMessage("handshake")
        assertEquals(1, failures)
        assertSame(replacement, client.getWebView())
        assertEquals(1, staticClock.scheduledTasks.size)
        bridges.last().postMessage("handshake")
        assertTrue(staticClock.scheduledTasks.isEmpty())
        staticClock.execute(10_000)
        assertSame(replacement, client.getWebView())
        assertEquals(1, failures)
    }

    @Test
    fun `fatal HTTP main frame failure notifies immediately`() {
        var failures = 0
        val client = registerClient { failures++ }
        dispatcher.scheduler.runCurrent()
        client.onReceivedHttpError(
            client.getWebView() as WebView,
            mockk(relaxed = true) { every { isForMainFrame } returns true },
            mockk(relaxed = true) { every { statusCode } returns 503 }
        )
        assertEquals(1, failures)
        assertNull(client.getWebView())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy main frame failure notifies immediately`() {
        var failures = 0
        val client = registerClient { failures++ }
        dispatcher.scheduler.runCurrent()
        client.onReceivedError(
            client.getWebView() as WebView,
            -2,
            "host lookup",
            "https://example.com"
        )
        assertEquals(1, failures)
        assertNull(client.getWebView())
    }

    @Test
    fun `missing required template notifies after teardown`() {
        every { mockAssets.open("InAppFormsTemplate.html") } throws FileNotFoundException(
            "template"
        )
        var failures = 0
        val client = registerClient { failures++ }
        dispatcher.scheduler.runCurrent()
        assertEquals(1, failures)
        assertNull(client.getWebView())
        assertTrue(staticClock.scheduledTasks.isEmpty())
        verifyDestroy()
    }

    @Test
    fun `renderer failure before handshake notifies once`() {
        var failures = 0
        val client = registerClient { failures++ }
        dispatcher.scheduler.runCurrent()
        val view = client.getWebView() as WebView
        assertTrue(client.onRenderProcessGone(view, mockk(relaxed = true)))
        assertTrue(client.onRenderProcessGone(view, mockk(relaxed = true)))
        assertEquals(1, failures)
        assertNull(client.getWebView())
        verifyDestroy()
    }

    @Test
    fun `missing optional JWT subresource errors and successful handshake never notify failure`() {
        var failures = 0
        val client = registerClient { failures++ }
        dispatcher.scheduler.runCurrent()
        val view = client.getWebView() as WebView
        val subresource = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns false
        }
        client.onReceivedError(view, subresource, mockk<WebResourceError>(relaxed = true))
        client.onReceivedHttpError(view, subresource, mockk(relaxed = true))
        assertSame(view, client.getWebView())
        client.onJsHandshakeCompleted()
        staticClock.execute(10_000)
        client.onReceivedError(
            view,
            mockk(relaxed = true) { every { isForMainFrame } returns true },
            null
        )
        assertEquals(0, failures)
        assertSame(view, client.getWebView())
        client.onRenderProcessGone(view, mockk(relaxed = true))
        assertEquals(0, failures)
        assertNull(client.getWebView())
    }

    @Test
    fun `explicit unregister while awaiting JWT is silent and releases registered handler`() {
        fakeAuth.providerRegistered = true
        var failures = 0
        val config = InAppFormsConfig(onFailure = { failures++ })
        val client = registerClient(config)
        dispatcher.scheduler.runCurrent()
        KlaviyoFormsProvider().unregister()
        fakeAuth.completeFetch(JWT)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(client.getWebView())
        assertNull(Registry.getOrNull<InAppFormsConfig>())
        assertTrue(config.onFailure != null)
        assertEquals(0, failures)
        verify(exactly = 0) { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) }
    }

    @Test
    fun `running attempt keeps its handler when registration changes future config`() {
        fakeAuth.providerRegistered = true
        var firstFailures = 0
        var futureFailures = 0
        val client = registerClient { firstFailures++ }
        dispatcher.scheduler.runCurrent()
        val view = client.getWebView()
        KlaviyoFormsProvider().register(InAppFormsConfig(onFailure = { futureFailures++ }))
        assertSame(view, client.getWebView())
        fakeAuth.completeFetch(JWT)
        dispatcher.scheduler.runCurrent()
        staticClock.execute(1_000)
        assertEquals(1, firstFailures)
        assertEquals(0, futureFailures)
        client.initializeWebView()
        dispatcher.scheduler.runCurrent()
        staticClock.execute(1_000)
        assertEquals(1, firstFailures)
        assertEquals(1, futureFailures)
    }

    @Test
    fun `throwing failure handler is contained and registration can be retried`() {
        val error = IllegalStateException("host callback")
        var failures = 0
        val client = registerClient {
            failures++
            throw error
        }
        dispatcher.scheduler.runCurrent()
        staticClock.execute(1_000)
        assertEquals(1, failures)
        assertNull(client.getWebView())
        verify { spyLog.error(any(), error) }
        client.initializeWebView()
        dispatcher.scheduler.runCurrent()
        staticClock.execute(1_000)
        assertEquals(2, failures)
    }

    @Test
    fun `automatic rebuild retains accepted handler and replacement is silent`() {
        var failures = 0
        Registry.register<FormsProvider>(KlaviyoFormsProvider())
        val client = registerClient { failures++ }
        dispatcher.scheduler.runCurrent()
        val original = client.getWebView()
        Klaviyo.reInitializeInAppForms()
        dispatcher.scheduler.runCurrent()
        assertNotSame(original, client.getWebView())
        assertEquals(0, failures)
        staticClock.execute(1_000)
        assertEquals(1, failures)
    }

    @Test
    fun `modern bridge rejects old view and completes handshake for current view`() {
        val bridges = mutableListOf<NativeBridge>()
        every { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) } answers {
            bridges += thirdArg<NativeBridge>()
        }
        var failures = 0
        val client = registerClient { failures++ }
        every { mockBridge.onPostMessage(any(), any(), any(), any(), any()) } answers {
            client.onJsHandshakeCompleted()
        }
        dispatcher.scheduler.runCurrent()
        val oldView = client.getWebView() as WebView
        client.destroyWebView()
        client.initializeWebView()
        dispatcher.scheduler.runCurrent()
        bridges.first().onPostMessage(oldView, mockk<WebMessageCompat>(), mockk(), true, mockk())
        assertEquals(1, staticClock.scheduledTasks.size)
        bridges.last().onPostMessage(
            client.getWebView() as WebView,
            mockk<WebMessageCompat>(),
            mockk(),
            true,
            mockk()
        )
        assertTrue(staticClock.scheduledTasks.isEmpty())
        staticClock.execute(1_000)
        assertEquals(0, failures)
    }

    @Test
    fun `failure handler may unregister synchronously`() {
        var failures = 0
        val client = registerClient {
            failures++
            KlaviyoFormsProvider().unregister()
        }
        dispatcher.scheduler.runCurrent()
        staticClock.execute(1_000)
        assertEquals(1, failures)
        assertNull(client.getWebView())
        assertNull(Registry.getOrNull<InAppFormsConfig>())
        assertTrue(staticClock.scheduledTasks.isEmpty())
    }

    @Test
    fun `queued cancellation of old view cannot destroy a handler retry`() {
        val client = registerClient {
            KlaviyoFormsProvider().register(InAppFormsConfig())
        }
        dispatcher.scheduler.runCurrent()
        val jobs = mutableListOf<() -> Unit>()
        var queueUiWork = true
        every { mockThreadHelper.runOnUiThread(any()) } answers {
            if (queueUiWork) jobs += firstArg<() -> Unit>() else firstArg<() -> Unit>().invoke()
        }
        staticClock.scheduledTasks.single().task()
        client.destroyWebView()
        assertEquals(2, jobs.size)
        queueUiWork = false
        jobs.first().invoke()
        val replacement = client.getWebView()
        assertTrue(replacement != null)
        jobs.last().invoke()
        assertSame(replacement, client.getWebView())
    }

    private fun captureAttemptBridges(): MutableList<NativeBridge> {
        Registry.register<NativeBridge>(KlaviyoNativeBridge())
        Registry.register<FormsProvider>(KlaviyoFormsProvider())
        return mutableListOf<NativeBridge>().also { bridges ->
            every { anyConstructed<KlaviyoWebView>().loadTemplate(any(), any(), any()) } answers {
                bridges += thirdArg<NativeBridge>()
            }
        }
    }

    private fun sendAbort(bridge: NativeBridge, view: WebView, modern: Boolean) {
        val message = """{"type":"abort","data":{"reason":"Native bridge is malformed"}}"""
        if (modern) {
            bridge.onPostMessage(view, WebMessageCompat(message, null), mockk(), true, mockk())
        } else {
            bridge.postMessage(message)
        }
    }

    private fun assertPreHandshakeAbortAllowsRetry(modern: Boolean) {
        val bridges = captureAttemptBridges()
        val client = KlaviyoWebViewClient()
        Registry.register<WebViewClient>(client)
        Registry.register<PresentationManager>(mockk<PresentationManager>(relaxed = true))
        var failures = 0
        KlaviyoFormsProvider().register(
            InAppFormsConfig(onFailure = {
                failures++
                assertNull(client.getWebView())
                assertTrue(staticClock.scheduledTasks.isEmpty())
                verify(exactly = 1) { mockObserverCollection.stopObservers() }
                verify(exactly = 1) { anyConstructed<KlaviyoWebView>().destroy() }
                KlaviyoFormsProvider().register(InAppFormsConfig(onFailure = { failures++ }))
            })
        )
        dispatcher.scheduler.runCurrent()
        val oldView = client.getWebView() as WebView
        val oldBridge = bridges.single()
        val oldTimer = staticClock.scheduledTasks.single().task
        sendAbort(oldBridge, oldView, modern)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, failures)
        val replacement = client.getWebView()
        assertTrue(replacement != null)
        assertNotSame(oldView, replacement)
        oldTimer()
        sendAbort(oldBridge, oldView, modern = false)
        sendAbort(oldBridge, oldView, modern = true)
        oldBridge.postMessage("""{"type":"handShook"}""")
        assertSame(replacement, client.getWebView())
        assertEquals(1, staticClock.scheduledTasks.size)
        assertEquals(1, failures)

        bridges.last().postMessage("""{"type":"handShook"}""")
        staticClock.execute(1_000)
        assertSame(replacement, client.getWebView())
        assertEquals(1, failures)
    }

    @Test
    fun `legacy pre-handshake Abort notifies after teardown and stale messages cannot break retry`() =
        assertPreHandshakeAbortAllowsRetry(modern = false)

    @Test
    fun `modern pre-handshake Abort notifies after teardown and stale messages cannot break retry`() =
        assertPreHandshakeAbortAllowsRetry(modern = true)

    private fun assertPostHandshakeAbortIsSilent(modern: Boolean) {
        val bridges = captureAttemptBridges()
        var failures = 0
        val client = registerClient { failures++ }
        dispatcher.scheduler.runCurrent()
        val view = client.getWebView() as WebView
        val bridge = bridges.single()
        bridge.postMessage("""{"type":"handShook"}""")
        sendAbort(bridge, view, modern)
        assertEquals(0, failures)
        assertNull(client.getWebView())
        assertNull(Registry.getOrNull<InAppFormsConfig>())
        assertTrue(staticClock.scheduledTasks.isEmpty())
        verifyDestroy()
    }

    @Test
    fun `legacy post-handshake Abort unregisters without failure callback`() =
        assertPostHandshakeAbortIsSilent(modern = false)

    @Test
    fun `modern post-handshake Abort unregisters without failure callback`() =
        assertPostHandshakeAbortIsSilent(modern = true)

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
        verify { anyConstructed<KlaviyoWebView>().loadTemplate(any(), client, any()) }
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
    fun `pending attempt keeps replay configuration while the next attempt accepts replacement`() {
        val replayed = mutableListOf<String?>()
        val launch = Event("launch").apply { uniqueId = "original-launch" }
        every { mockState.getBufferedEvents(any()) } answers {
            if (firstArg<Long>() == 20_000L) listOf(launch.copy()) else emptyList()
        }
        every { mockJsBridge.profileEvent(any()) } answers {
            replayed.add(firstArg<Event>().uniqueId)
            Unit
        }
        val observers = object : JsBridgeObserverCollection {
            override val observers = listOf(ProfileEventObserver())
        }
        Registry.register<JsBridgeObserverCollection>(observers)
        Registry.register<InAppFormsConfig>(InAppFormsConfig(eventReplayWindow = 20.seconds))
        fakeAuth.providerRegistered = true
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        dispatcher.scheduler.runCurrent()

        Registry.register<InAppFormsConfig>(InAppFormsConfig(eventReplayWindow = 0.seconds))
        client.initializeWebView()
        fakeAuth.completeFetch(JWT)
        dispatcher.scheduler.runCurrent()
        client.onJsHandshakeCompleted()
        assertEquals(listOf("original-launch"), replayed)

        client.destroyWebView()
        replayed.clear()
        client.initializeWebView()
        dispatcher.scheduler.runCurrent()
        client.onJsHandshakeCompleted()
        assertTrue(replayed.isEmpty())
        client.destroyWebView()
        Registry.unregister<InAppFormsConfig>()
    }

    @Test
    fun `load budget is captured before JWT wait and starts when template loads`() {
        Registry.register<InAppFormsConfig>(InAppFormsConfig(loadTimeout = 30.seconds))
        fakeAuth.providerRegistered = true
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        dispatcher.scheduler.runCurrent()
        Registry.register<InAppFormsConfig>(InAppFormsConfig(loadTimeout = 5.seconds))
        dispatcher.scheduler.advanceTimeBy(AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, staticClock.scheduledTasks.size)
        assertEquals(TIME + 30_000, staticClock.scheduledTasks.single().time)
        staticClock.execute(29_999)
        assertTrue(client.getWebView() != null)
        staticClock.execute(1)
        assertNull(client.getWebView())
        Registry.unregister<InAppFormsConfig>()
    }

    @Test
    fun `omitted load budget uses the network timeout`() {
        Registry.register<InAppFormsConfig>(InAppFormsConfig())
        KlaviyoWebViewClient().initializeWebView()
        dispatcher.scheduler.runCurrent()
        assertEquals(TIME + 1_000, staticClock.scheduledTasks.single().time)
        Registry.unregister<InAppFormsConfig>()
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
        KlaviyoWebViewClient().apply {
            initializeWebView()
            onLocalJsReady()
        }
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

        verify { mockObserverCollection.startObservers(NativeBridgeMessage.HandShook, 10.seconds) }
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
        verify(exactly = 3) { mockThreadHelper.runOnUiThread(any()) }
    }

    @Test
    fun `onRenderProcessGone handles webview crash`() {
        val mockPresentationManager = mockk<PresentationManager>(relaxed = true).apply {
            every { dismiss() } just runs
        }
        Registry.register<PresentationManager>(mockPresentationManager)
        val client = KlaviyoWebViewClient()
        client.initializeWebView()
        val mockDetail: RenderProcessGoneDetail = mockk(relaxed = true)
        val result = client.onRenderProcessGone(client.getWebView() as WebView, mockDetail)

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
