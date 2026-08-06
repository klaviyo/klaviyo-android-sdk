package com.klaviyo.pushFcm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants.PACKAGE_PREFIX
import com.klaviyo.core.Constants.TRACKING_PARAMETER
import com.klaviyo.core.config.Clock
import com.klaviyo.core.config.KlaviyoConfig
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class KlaviyoTrampolineActivityTest : BaseTest() {

    private val mockBrowserIntent = mockk<Intent>(relaxed = true)
    private val mockDeepLinkIntent = mockk<Intent>(relaxed = true)
    private val mockLaunchIntent = mockk<Intent>(relaxed = true)
    private val trampolineContextPackageManager = mockk<PackageManager>(relaxed = true)
    private val mockTrampolineContext = mockk<Context>(relaxed = true).apply {
        every { packageManager } returns trampolineContextPackageManager
        every { startActivity(any()) } returns Unit
    }
    private val mockPendingDispatch = mockk<Clock.Cancellable>(relaxed = true)

    /** The postponed deep link dispatch, captured instead of run, so tests can drive its timing. */
    private var postponedDispatch: ((Activity) -> Unit)? = null

    /** The fallback the postponed dispatch registered for when no activity resumes in time. */
    private var postponedTimeout: (() -> Unit)? = null

    @Before
    override fun setup() {
        super.setup()
        // mockkStatic must precede mockkObject so @JvmStatic methods like
        // Klaviyo.handlePush get intercepted, not dispatched to the real impl.
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        mockkObject(DeepLinking)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { Klaviyo.handlePush(any()) } returns Klaviyo
        every { Klaviyo.handlePush(any(), any()) } returns true
        every { DeepLinking.makeExternalIntent(any()) } returns mockBrowserIntent
        every { DeepLinking.makeDeepLinkIntent(any(), any(), any()) } returns mockDeepLinkIntent
        every { DeepLinking.makeLaunchIntent(any(), any()) } returns mockLaunchIntent
        every { DeepLinking.handleDeepLink(any()) } returns Unit
        // No deep link handler registered by default; flip per-test for the handler branch.
        every { DeepLinking.isHandlerRegistered } returns false
        // Make intents appear resolvable so startActivityIfResolved actually dispatches to
        // context.startActivity (vs logging an error). Override per-test for the unresolvable case.
        every { mockBrowserIntent.resolveActivity(any()) } returns mockk()
        every { mockDeepLinkIntent.resolveActivity(any()) } returns mockk()
        every { mockLaunchIntent.resolveActivity(any()) } returns mockk()

        postponedDispatch = null
        postponedTimeout = null
        every {
            mockLifecycleMonitor.runWithCurrentOrNextActivity(any(), any(), any())
        } answers {
            postponedTimeout = secondArg()
            postponedDispatch = thirdArg()
            mockPendingDispatch
        }
    }

    @After
    override fun cleanup() {
        unmockkStatic(Uri::class)
        unmockkObject(DeepLinking)
        unmockkObject(Klaviyo)
        unmockkStatic(Klaviyo::class)
        super.cleanup()
    }

    /**
     * Build a mock Intent that looks like a Klaviyo notification tap intent.
     * Has `com.klaviyo._k` extra so `isKlaviyoNotificationIntent` returns true.
     */
    private fun klaviyoIntent(): Intent = mockk(relaxed = true) {
        every { getStringExtra(PACKAGE_PREFIX + TRACKING_PARAMETER) } returns "tracking-id"
        every { getStringExtra(KlaviyoTrampolineActivity.BROWSER_URL_EXTRA) } returns null
        every { data } returns null
    }

    @Test
    fun `handleTrampolineIntent calls handlePush and launches browser intent`() {
        val intent = klaviyoIntent()
        every {
            intent.getStringExtra(KlaviyoTrampolineActivity.BROWSER_URL_EXTRA)
        } returns "https://example.com"
        val parsedUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("https://example.com") } returns parsedUri

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent, false) }
        // Assert the exact URL from the extra round-trips through Uri.parse and into
        // makeExternalIntent — catches regressions where a string is mangled, swallowed,
        // or replaced silently with something else.
        verify { DeepLinking.makeExternalIntent(parsedUri) }
        verify { mockTrampolineContext.startActivity(mockBrowserIntent) }
    }

    @Test
    fun `handleTrampolineIntent with no deep link launches the host app`() {
        val intent = klaviyoIntent() // no browser extra, no deep link data

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent, false) }
        verify(exactly = 0) { DeepLinking.makeExternalIntent(any()) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any(), any()) }
        verify { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
    }

    @Test
    fun `handleTrampolineIntent with no launch intent warns and starts nothing`() {
        val intent = klaviyoIntent() // no browser extra, no deep link data
        // Host app has no launcher activity → makeLaunchIntent yields null.
        every { DeepLinking.makeLaunchIntent(any(), any()) } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent, false) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        // Mirrors the non-trampoline diagnostic so a missing launcher is still debuggable.
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent with deep link and no handler dispatches ACTION_VIEW into host`() {
        val intent = klaviyoIntent()
        val deepLink = mockk<Uri>(relaxed = true)
        every { intent.data } returns deepLink
        every { DeepLinking.isHandlerRegistered } returns false

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent, false) }
        verify { DeepLinking.makeDeepLinkIntent(deepLink, mockTrampolineContext, intent) }
        verify { mockTrampolineContext.startActivity(mockDeepLinkIntent) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any(), any()) }
    }

    /** A body tap carrying a deep link, with a host deep link handler registered. */
    private fun handlerIntent(deepLink: Uri): Intent = klaviyoIntent().also {
        every { it.data } returns deepLink
        every { DeepLinking.isHandlerRegistered } returns true
    }

    @Test
    fun `handleTrampolineIntent with deep link and handler brings host to the front`() {
        val deepLink = mockk<Uri>(relaxed = true)
        val intent = handlerIntent(deepLink)

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent, false) }
        // The handler owns navigation, so no ACTION_VIEW carrying the link.
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any(), any()) }
        verify { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
        // NEW_TASK alone: CLEAR_TOP would tear down whatever the handler navigates to, and
        // SINGLE_TOP would re-deliver an intent to the launcher activity.
        verify { mockLaunchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        verify(exactly = 0) { mockLaunchIntent.addFlags(any()) }
    }

    @Test
    fun `handleTrampolineIntent postpones handler dispatch until an activity resumes`() {
        val deepLink = mockk<Uri>(relaxed = true)
        val intent = handlerIntent(deepLink)

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify {
            mockLifecycleMonitor.runWithCurrentOrNextActivity(
                KlaviyoTrampolineActivity.HANDLER_DISPATCH_TIMEOUT,
                any(),
                any()
            )
        }
        verify(exactly = 0) { DeepLinking.handleDeepLink(any()) }

        assertNotNull(postponedDispatch)
        postponedDispatch?.invoke(mockActivity)

        verify(exactly = 1) { DeepLinking.handleDeepLink(deepLink) }
    }

    @Test
    fun `duplicate delivery brings the host to the front without dispatching again`() {
        // Dispatch moved out of handlePush, so it no longer sits behind that dedup guard. Without
        // honoring the reported outcome, a re-delivered intent navigates the host a second time.
        val deepLink = mockk<Uri>(relaxed = true)
        val intent = handlerIntent(deepLink)
        every { Klaviyo.handlePush(intent, false) } returns false

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify(exactly = 0) {
            mockLifecycleMonitor.runWithCurrentOrNextActivity(any(), any(), any())
        }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
    }

    @Test
    fun `postponed handler dispatch contains a throwing handler`() {
        // The postponed job runs inside the host's ActivityLifecycleCallbacks broadcast, so an
        // exception escaping it would crash the host app rather than the SDK.
        mockkObject(KlaviyoConfig)
        try {
            every { KlaviyoConfig.isDebugBuild } returns false
            val deepLink = mockk<Uri>(relaxed = true)
            every {
                DeepLinking.handleDeepLink(deepLink)
            } throws RuntimeException("host handler blew up")

            KlaviyoTrampolineActivity.handleTrampolineIntent(
                handlerIntent(deepLink),
                mockTrampolineContext
            )
            assertNotNull(postponedDispatch)
            postponedDispatch?.invoke(mockActivity)

            verify { spyLog.error(any(), any<Exception>()) }
        } finally {
            unmockkObject(KlaviyoConfig)
        }
    }

    @Test
    fun `handleTrampolineIntent registers the postponed dispatch before starting the host`() {
        val intent = handlerIntent(mockk(relaxed = true))

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        // Registering after the launch could miss a resume that beats the registration.
        verifyOrder {
            mockLifecycleMonitor.runWithCurrentOrNextActivity(any(), any(), any())
            mockTrampolineContext.startActivity(mockLaunchIntent)
        }
    }

    @Test
    fun `postponed handler dispatch is dropped rather than invoked when it times out`() {
        val intent = handlerIntent(mockk(relaxed = true))

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)
        assertNotNull(postponedTimeout)
        postponedTimeout?.invoke()

        // Invoking a handler that needs an activity when none resumed would lose the link anyway.
        verify(exactly = 0) { DeepLinking.handleDeepLink(any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent abandons the postponed dispatch when host has no launch intent`() {
        val intent = handlerIntent(mockk(relaxed = true))
        every { DeepLinking.makeLaunchIntent(any(), any()) } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        // Nothing will bring the host up, so don't park an observer for the process lifetime.
        verify { mockPendingDispatch.cancel() }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent with unresolvable deep link falls back to launcher`() {
        val intent = klaviyoIntent()
        val deepLink = mockk<Uri>(relaxed = true)
        every { intent.data } returns deepLink
        every { DeepLinking.isHandlerRegistered } returns false
        // Deep link target cannot be resolved → fall back to the launcher.
        every { mockDeepLinkIntent.resolveActivity(any()) } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { DeepLinking.makeDeepLinkIntent(deepLink, mockTrampolineContext, intent) }
        verify { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(mockDeepLinkIntent) }
        // Degraded-but-handled (fell back to launcher) → WARNING, not ERROR.
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent ignores non-Klaviyo intent`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(any()) } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify(exactly = 0) { Klaviyo.handlePush(any(), any()) }
        verify(exactly = 0) { DeepLinking.makeExternalIntent(any()) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent ignores null intent`() {
        KlaviyoTrampolineActivity.handleTrampolineIntent(null, mockTrampolineContext)

        verify(exactly = 0) { Klaviyo.handlePush(any(), any()) }
        verify(exactly = 0) { DeepLinking.makeExternalIntent(any()) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent dispatches non-web scheme URL via makeExternalIntent`() {
        val intent = klaviyoIntent()
        every {
            intent.getStringExtra(KlaviyoTrampolineActivity.BROWSER_URL_EXTRA)
        } returns "mailto:user@example.com"
        val parsedUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("mailto:user@example.com") } returns parsedUri

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent, false) }
        verify { DeepLinking.makeExternalIntent(parsedUri) }
        verify { mockTrampolineContext.startActivity(mockBrowserIntent) }
    }
}
