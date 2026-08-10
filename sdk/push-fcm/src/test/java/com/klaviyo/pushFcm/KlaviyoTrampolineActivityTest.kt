package com.klaviyo.pushFcm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants
import com.klaviyo.core.Constants.PACKAGE_PREFIX
import com.klaviyo.core.Constants.TRACKING_PARAMETER
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
    fun `handleTrampolineIntent with a resolvable deep link dispatches ACTION_VIEW into host`() {
        val intent = klaviyoIntent()
        val deepLink = mockk<Uri>(relaxed = true)
        every { intent.data } returns deepLink
        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent) }
        verify { DeepLinking.makeDeepLinkIntent(deepLink, mockTrampolineContext, intent) }
        verify { mockTrampolineContext.startActivity(mockDeepLinkIntent) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any(), any()) }
    }

    @Test
    fun `handleTrampolineIntent dispatches ACTION_VIEW even when a handler is registered`() {
        val intent = klaviyoIntent()
        val deepLink = mockk<Uri>(relaxed = true)
        every { intent.data } returns deepLink
        every { DeepLinking.isHandlerRegistered } returns true

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        // The link reaches the host on this intent, so the handler is not invoked here.
        verify { Klaviyo.handlePush(intent) }
        verify(exactly = 0) { DeepLinking.handleDeepLink(any()) }
        verify { DeepLinking.makeDeepLinkIntent(deepLink, mockTrampolineContext, intent) }
        verify { mockTrampolineContext.startActivity(mockDeepLinkIntent) }
    }

    @Test
    fun `handleTrampolineIntent with unresolvable deep link falls back to launcher`() {
        val intent = klaviyoIntent()
        val deepLink = mockk<Uri>(relaxed = true)
        every { intent.data } returns deepLink
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
    fun `handleTrampolineIntent attaches an unresolvable deep link to the launcher intent`() {
        val intent = klaviyoIntent()
        val deepLink = mockk<Uri>(relaxed = true)
        every { intent.data } returns deepLink
        every { mockDeepLinkIntent.resolveActivity(any()) } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        // Without this the link would be dropped, since the launcher intent carries no data.
        verify { mockLaunchIntent.data = deepLink }
        verify { mockTrampolineContext.startActivity(mockLaunchIntent) }
    }

    @Test
    fun `handleTrampolineIntent strips the suppression flag from the deep link intent`() {
        val intent = klaviyoIntent()
        every { intent.data } returns mockk<Uri>(relaxed = true)

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        // Both destinations copy this intent's extras. If the flag survived, the host's own
        // handlePush would silently stop invoking their registered handler.
        verify { mockDeepLinkIntent.removeExtra(Constants.SUPPRESS_DEEP_LINK_HANDLER_EXTRA) }
    }

    @Test
    fun `handleTrampolineIntent strips the suppression flag from the launcher intent`() {
        val intent = klaviyoIntent()

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { mockLaunchIntent.removeExtra(Constants.SUPPRESS_DEEP_LINK_HANDLER_EXTRA) }
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
