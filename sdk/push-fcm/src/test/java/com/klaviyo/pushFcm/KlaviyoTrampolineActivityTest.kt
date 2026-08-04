package com.klaviyo.pushFcm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants.PACKAGE_PREFIX
import com.klaviyo.core.Constants.TRACKING_PARAMETER
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
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

    /**
     * Flags the trampoline adds to every destination it owns, mirroring the non-trampoline content
     * intent. Spelled out here rather than referencing the production expression so a change there
     * fails these tests. The one exception is a deep link with a handler registered, which must
     * only bring the host task to the front — see the MAGE-1004 regression test below.
     */
    private val resetFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_SINGLE_TOP or
        Intent.FLAG_ACTIVITY_CLEAR_TOP

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
        every { DeepLinking.makeExternalIntent(any()) } returns mockBrowserIntent
        every { DeepLinking.makeDeepLinkIntent(any(), any(), any()) } returns mockDeepLinkIntent
        every { DeepLinking.makeLaunchIntent(any(), any()) } returns mockLaunchIntent
        // No deep link handler registered by default; flip per-test for the handler branch.
        every { DeepLinking.isHandlerRegistered } returns false
        // Make intents appear resolvable so startActivityIfResolved actually dispatches to
        // context.startActivity (vs logging an error). Override per-test for the unresolvable case.
        every { mockBrowserIntent.resolveActivity(any()) } returns mockk()
        every { mockDeepLinkIntent.resolveActivity(any()) } returns mockk()
        every { mockLaunchIntent.resolveActivity(any()) } returns mockk()
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

        verify { Klaviyo.handlePush(intent) }
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

        verify { Klaviyo.handlePush(intent) }
        verify(exactly = 0) { DeepLinking.makeExternalIntent(any()) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any(), any()) }
        verify { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
        // No handler navigation to preserve on an open_app tap, so reset to root as before.
        verify { mockLaunchIntent.addFlags(resetFlags) }
        verify(exactly = 0) { mockLaunchIntent.setFlags(any()) }
    }

    @Test
    fun `handleTrampolineIntent with no launch intent warns and starts nothing`() {
        val intent = klaviyoIntent() // no browser extra, no deep link data
        // Host app has no launcher activity → makeLaunchIntent yields null.
        every { DeepLinking.makeLaunchIntent(any(), any()) } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent) }
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

        verify { Klaviyo.handlePush(intent) }
        verify { DeepLinking.makeDeepLinkIntent(deepLink, mockTrampolineContext, intent) }
        verify { mockTrampolineContext.startActivity(mockDeepLinkIntent) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any(), any()) }
        // The SDK owns this navigation, so it keeps the reset-to-root behavior.
        verify { mockDeepLinkIntent.addFlags(resetFlags) }
        verify(exactly = 0) { mockDeepLinkIntent.setFlags(any()) }
    }

    @Test
    fun `handleTrampolineIntent with deep link and handler registered brings host to front without clearing back stack`() {
        val intent = klaviyoIntent()
        val deepLink = mockk<Uri>(relaxed = true)
        every { intent.data } returns deepLink
        // handlePush already dispatched the registered handler — a VIEW intent would double-deliver.
        every { DeepLinking.isHandlerRegistered } returns true

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any(), any()) }
        verify { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
        // The handler either has navigated already or is postponed until this intent resumes the
        // host: CLEAR_TOP would finish that destination and SINGLE_TOP would re-deliver an
        // ACTION_MAIN intent to the launcher activity. Assigned, not added, because
        // makeLaunchIntent bakes in SINGLE_TOP — see DeepLinkingTest's makeLaunchIntent coverage.
        verify { mockLaunchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 0) { mockLaunchIntent.addFlags(any()) }
    }

    @Test
    fun `handleTrampolineIntent with deep link and handler registered brings host to front on cold start`() {
        val intent = klaviyoIntent()
        val deepLink = mockk<Uri>(relaxed = true)
        every { intent.data } returns deepLink
        every { DeepLinking.isHandlerRegistered } returns true
        // Cold start: no activity exists yet, so DeepLinking postpones the handler until the
        // intent started here resumes the host. Dispatch must not depend on that state — this
        // launch is what creates the task the handler will navigate on top of.
        every { Registry.lifecycleMonitor.currentActivity } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
        verify { mockLaunchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 0) { mockLaunchIntent.addFlags(any()) }
    }

    @Test
    fun `handleTrampolineIntent with handler registered but no deep link still clears top`() {
        val intent = klaviyoIntent() // open_app tap: handler registered, but no link to dispatch
        every { DeepLinking.isHandlerRegistered } returns true

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        // Without a deep link the handler was never invoked, so there is no host navigation to
        // preserve and this stays a plain launch — registering a handler must not change open_app.
        verify { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
        verify { mockLaunchIntent.addFlags(resetFlags) }
        verify(exactly = 0) { mockLaunchIntent.setFlags(any()) }
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
        // No handler ran for this tap, so the fallback keeps the reset-to-root behavior.
        verify { mockLaunchIntent.addFlags(resetFlags) }
        // Degraded-but-handled (fell back to launcher) → WARNING, not ERROR.
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent ignores non-Klaviyo intent`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(any()) } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
        verify(exactly = 0) { DeepLinking.makeExternalIntent(any()) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent ignores null intent`() {
        KlaviyoTrampolineActivity.handleTrampolineIntent(null, mockTrampolineContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
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

        verify { Klaviyo.handlePush(intent) }
        verify { DeepLinking.makeExternalIntent(parsedUri) }
        verify { mockTrampolineContext.startActivity(mockBrowserIntent) }
    }
}
