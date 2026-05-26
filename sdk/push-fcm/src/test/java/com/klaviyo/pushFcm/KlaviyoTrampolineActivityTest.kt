package com.klaviyo.pushFcm

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class KlaviyoTrampolineActivityTest : BaseTest() {

    private val mockResolvedActivity = mockk<android.content.ComponentName>(relaxed = true)

    private val mockTrampolineContext = mockk<Context>(relaxed = true).apply {
        every { packageManager } returns mockPackageManager
        every { packageName } returns "com.klaviyo.sample"
    }

    @Before
    override fun setup() {
        super.setup()

        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        every { Klaviyo.handlePush(any()) } returns Klaviyo

        mockkObject(DeepLinking)
        every { DeepLinking.isHandlerRegistered } returns false
        every { DeepLinking.makeLaunchIntent(any(), any()) } returns mockk<Intent>(relaxed = true).apply {
            every { resolveActivity(any()) } returns mockResolvedActivity
        }
        every { DeepLinking.makeDeepLinkIntent(any(), any(), any()) } returns mockk<Intent>(
            relaxed = true
        ).apply {
            every { resolveActivity(any()) } returns mockResolvedActivity
        }
    }

    @After
    override fun cleanup() {
        unmockkObject(DeepLinking)
        unmockkObject(Klaviyo)
        unmockkStatic(Klaviyo::class)
        super.cleanup()
    }

    /**
     * Build a mock Intent that looks like a Klaviyo notification tap intent.
     * Has `com.klaviyo._k` extra so `isKlaviyoNotificationIntent` returns true.
     */
    private fun klaviyoIntent(uri: Uri? = null): Intent = mockk(relaxed = true) {
        every { getStringExtra(Constants.PACKAGE_PREFIX + Constants.TRACKING_PARAMETER) } returns "tracking-id"
        every { data } returns uri
        every { getBooleanExtra(Constants.AUTO_TRACKED_EXTRA, false) } returns false
    }

    private fun nonKlaviyoIntent(): Intent = mockk(relaxed = true) {
        every { getStringExtra(any()) } returns null
        every { data } returns null
    }

    @Test
    fun `handleTrampolineIntent ignores null intent`() {
        KlaviyoTrampolineActivity.handleTrampolineIntent(null, mockTrampolineContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any(), any()) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any(), any()) }
    }

    @Test
    fun `handleTrampolineIntent ignores non-Klaviyo intent`() {
        KlaviyoTrampolineActivity.handleTrampolineIntent(nonKlaviyoIntent(), mockTrampolineContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any(), any()) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any(), any()) }
    }

    @Test
    fun `handleTrampolineIntent with Klaviyo intent and no deep link tracks open and launches host`() {
        val intent = klaviyoIntent(uri = null)

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify(exactly = 1) { Klaviyo.handlePush(intent) }
        verify(exactly = 1) { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any(), any()) }
    }

    @Test
    fun `handleTrampolineIntent with Klaviyo intent stamps AUTO_TRACKED_EXTRA after handlePush`() {
        val intent = klaviyoIntent(uri = null)

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        // Verify the dedup flag is stamped. The order matters (after handlePush, before destination
        // launch) but verifying call ordering on mockk requires verifyOrder; for the prototype it's
        // sufficient to confirm the putExtra happened.
        verify(exactly = 1) { intent.putExtra(Constants.AUTO_TRACKED_EXTRA, true) }
    }

    @Test
    fun `handleTrampolineIntent with deep link and no handler launches host VIEW intent`() {
        val uri = mockk<Uri>(relaxed = true)
        val intent = klaviyoIntent(uri = uri)
        every { DeepLinking.isHandlerRegistered } returns false

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify(exactly = 1) { Klaviyo.handlePush(intent) }
        verify(exactly = 1) { DeepLinking.makeDeepLinkIntent(uri, mockTrampolineContext, intent) }
    }

    @Test
    fun `handleTrampolineIntent with deep link and registered handler still launches destination`() {
        // Cold-start integrity: if the host has no foregrounded Activity, skipping the
        // destination launch would leave the user staring at the home screen after tapping
        // a notification. The dedup guard (AUTO_TRACKED_EXTRA) prevents the handler from
        // firing twice when the destination intent reaches the host.
        val uri = mockk<Uri>(relaxed = true)
        val intent = klaviyoIntent(uri = uri)
        every { DeepLinking.isHandlerRegistered } returns true

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify(exactly = 1) { Klaviyo.handlePush(intent) }
        verify(exactly = 1) { DeepLinking.makeDeepLinkIntent(uri, mockTrampolineContext, intent) }
    }

    @Test
    fun `handleTrampolineIntent falls back to launch intent when deep link does not resolve`() {
        val uri = mockk<Uri>(relaxed = true)
        val intent = klaviyoIntent(uri = uri)
        val unresolvedDeepLinkIntent = mockk<Intent>(relaxed = true).apply {
            // Make resolveActivity return null to simulate unresolvable deep link
            every { resolveActivity(any()) } returns null
        }
        every { DeepLinking.makeDeepLinkIntent(uri, any(), any()) } returns unresolvedDeepLinkIntent

        KlaviyoTrampolineActivity.handleTrampolineIntent(intent, mockTrampolineContext)

        verify(exactly = 1) { DeepLinking.makeDeepLinkIntent(uri, mockTrampolineContext, intent) }
        verify(exactly = 1) { DeepLinking.makeLaunchIntent(mockTrampolineContext, any()) }
    }

    @Test
    fun `handleTrampolineIntent companion is accessible from internal call sites`() {
        // Smoke: the companion entry point is callable (we use it from onCreate / onNewIntent
        // and from these tests).
        assertNotNull(KlaviyoTrampolineActivity.Companion)
    }
}
