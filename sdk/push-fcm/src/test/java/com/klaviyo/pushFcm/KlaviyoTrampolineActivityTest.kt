package com.klaviyo.pushFcm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
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
        every { DeepLinking.makeBrowserIntent(any()) } returns mockBrowserIntent
        // Make the browser intent appear resolvable so startActivityIfResolved
        // actually dispatches to context.startActivity (vs logging an error).
        every { mockBrowserIntent.resolveActivity(any()) } returns mockk()
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
    }

    @Test
    fun `handleTrampolineIntent calls handlePush and launches browser intent`() {
        val intent = klaviyoIntent()
        every {
            intent.getStringExtra(KlaviyoTrampolineActivity.BROWSER_URL_EXTRA)
        } returns "https://example.com"

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify { Klaviyo.handlePush(intent) }
        verify { DeepLinking.makeBrowserIntent(any()) }
        verify { mockTrampolineContext.startActivity(mockBrowserIntent) }
    }

    @Test
    fun `handleTrampolineIntent with Klaviyo intent but no dispatch extra tracks open and warns`() {
        val intent = klaviyoIntent()
        every {
            intent.getStringExtra(KlaviyoTrampolineActivity.BROWSER_URL_EXTRA)
        } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        // handlePush still runs for any Klaviyo notification intent — only dispatch is skipped.
        verify { Klaviyo.handlePush(intent) }
        verify(exactly = 0) { DeepLinking.makeBrowserIntent(any()) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent ignores non-Klaviyo intent`() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(any()) } returns null

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
        verify(exactly = 0) { DeepLinking.makeBrowserIntent(any()) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent ignores null intent`() {
        KlaviyoTrampolineActivity.handleTrampolineIntent(null, mockTrampolineContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
        verify(exactly = 0) { DeepLinking.makeBrowserIntent(any()) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }
}
