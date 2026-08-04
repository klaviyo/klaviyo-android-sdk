package com.klaviyo.analytics.linking

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.klaviyo.core.Registry
import com.klaviyo.core.config.KlaviyoConfig
import com.klaviyo.core.lifecycle.LifecycleMonitor.Companion.COLD_START_GRACE_PERIOD
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class DeepLinkingTest : BaseTest() {

    private val testUrl = "https://example.com/u/slug"
    private val mockUri = mockk<Uri>(relaxed = true)
    private val testActivity = mockk<Activity>(relaxed = true)
    private val testPackageManager = mockk<PackageManager>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        mockkStatic(Uri::class)
        every { Uri.parse(testUrl) } returns mockUri

        // Setup Intent mocking
        MockIntent.setupIntentMocking()

        every { mockContext.packageManager } returns testPackageManager
        every { mockContext.packageName } returns "com.test.app"
        every { testActivity.packageName } returns "com.test.app"
        every { testActivity.startActivity(any()) } returns Unit
        every { mockContext.startActivity(any()) } returns Unit
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkAll()
        Registry.unregister<DeepLinkHandler>()
    }

    @Test
    fun `isHandlerRegistered returns false when no handler registered`() {
        assertFalse(DeepLinking.isHandlerRegistered)
    }

    @Test
    fun `isHandlerRegistered returns true when handler is registered`() {
        Registry.register<DeepLinkHandler>(mockk<DeepLinkHandler>(relaxed = true))
        assertTrue(DeepLinking.isHandlerRegistered)
    }

    @Test
    fun `handleDeepLink invokes registered handler when available`() {
        var invokedUri: Uri? = null
        val handler = object : DeepLinkHandler {
            override fun invoke(uri: Uri) {
                invokedUri = uri
            }
        }
        Registry.register<DeepLinkHandler>(handler)

        DeepLinking.handleDeepLink(mockUri)

        assertEquals(mockUri, invokedUri)
        verify(exactly = 1) { mockThreadHelper.runOnUiThread(any()) }
        // The two branches are mutually exclusive: a registered handler owns the navigation, so
        // broadcasting an intent as well would deliver it twice.
        verify(exactly = 0) { testActivity.startActivity(any()) }
        verify(exactly = 0) { mockActivity.startActivity(any()) }
    }

    @Test
    fun `handleDeepLink broadcasts intent when no handler registered`() {
        every { testActivity.startActivity(any()) } returns Unit
        runWithActivityImmediately()

        DeepLinking.handleDeepLink(mockUri)

        verify { testActivity.startActivity(any()) }
        verify(exactly = 0) { mockThreadHelper.runOnUiThread(any()) }
    }

    @Test
    fun `handleDeepLink sends no intent if link is unsupported`() {
        every { anyConstructed<Intent>().resolveActivity(any()) } returns null

        every { testActivity.startActivity(any()) } returns Unit
        runWithActivityImmediately()

        DeepLinking.handleDeepLink(mockUri)

        verify(inverse = true) { testActivity.startActivity(any()) }
    }

    @Test
    fun `handleDeepLink postpones handler until an activity resumes`() {
        var invokedUri: Uri? = null
        Registry.register<DeepLinkHandler>(DeepLinkHandler { uri -> invokedUri = uri })
        val job = capturePostponedJob()

        DeepLinking.handleDeepLink(mockUri)

        assertNull("Handler must not fire before an activity resumes", invokedUri)

        job.captured.invoke(testActivity)

        assertEquals(mockUri, invokedUri)
    }

    @Test
    fun `handleDeepLink waits for the cold start grace period`() {
        Registry.register<DeepLinkHandler>(mockk<DeepLinkHandler>(relaxed = true))
        val timeout = slot<Long>()
        every {
            Registry.lifecycleMonitor.runWithCurrentOrNextActivity(capture(timeout), any(), any())
        } returns null

        DeepLinking.handleDeepLink(mockUri)

        assertEquals(COLD_START_GRACE_PERIOD, timeout.captured)
    }

    @Test
    fun `handleDeepLink invokes handler anyway when no activity resumes in time`() {
        var invokedUri: Uri? = null
        Registry.register<DeepLinkHandler>(DeepLinkHandler { uri -> invokedUri = uri })
        val onTimeout = slot<() -> Unit>()
        every {
            Registry.lifecycleMonitor.runWithCurrentOrNextActivity(any(), capture(onTimeout), any())
        } returns null

        DeepLinking.handleDeepLink(mockUri)

        assertNull("Handler must not fire before the timeout elapses", invokedUri)

        onTimeout.captured.invoke()

        assertEquals(mockUri, invokedUri)
        verify { spyLog.warning(any(), any()) }
    }

    @Test
    fun `handleDeepLink does not propagate an exception from the host handler`() {
        Registry.register<DeepLinkHandler>(
            DeepLinkHandler { throw RuntimeException("host handler blew up") }
        )

        // safeCall re-throws in debug builds to avoid development blindness, so this asserts the
        // production behavior the guard exists for: a throwing handler must not crash the host.
        mockkObject(KlaviyoConfig)
        every { KlaviyoConfig.isDebugBuild } returns false

        DeepLinking.handleDeepLink(mockUri)

        verify { spyLog.error(any(), any<Exception>()) }
    }

    /**
     * Stub the postponement helper to run its job immediately with [testActivity].
     * [BaseTest] does this with its own `mockActivity`; these cases assert on `testActivity`.
     */
    private fun runWithActivityImmediately() {
        every {
            Registry.lifecycleMonitor.runWithCurrentOrNextActivity(any(), any(), any())
        } answers {
            thirdArg<(Activity) -> Unit>().invoke(testActivity)
            null
        }
    }

    /**
     * Stub the postponement helper to capture its job without running it, standing in for a host
     * with no resumed activity — a cold start, or an activity transition in progress.
     */
    private fun capturePostponedJob(): CapturingSlot<(Activity) -> Unit> {
        val job = slot<(Activity) -> Unit>()
        every {
            Registry.lifecycleMonitor.runWithCurrentOrNextActivity(any(), any(), capture(job))
        } returns null
        return job
    }

    @Test
    fun `sendLaunchIntent does nothing when no launch intent available`() {
        every { testPackageManager.getLaunchIntentForPackage("com.test.app") } returns null

        DeepLinking.sendLaunchIntent(mockContext)

        verify(exactly = 0) { mockContext.startActivity(any()) }
    }

    @Test
    fun `sendLaunchIntent invokes startActivity when launch intent exists`() {
        val mockLaunchIntent = MockIntent.setupIntentMocking().intent
        every { testPackageManager.getLaunchIntentForPackage("com.test.app") } returns mockLaunchIntent

        DeepLinking.sendLaunchIntent(mockContext)

        verify { mockContext.startActivity(mockLaunchIntent) }
    }

    @Test
    fun `makeDeepLinkIntent creates properly configured intent`() {
        val result = DeepLinking.makeDeepLinkIntent(mockUri, mockContext)

        assertEquals(mockUri, result.data)
        assertEquals(Intent.ACTION_VIEW, result.action)
        assertEquals("com.test.app", result.`package`)
        assertEquals(Intent.FLAG_ACTIVITY_SINGLE_TOP, result.flags)
    }

    @Test
    fun `makeDeepLinkIntent copies extras from copyIntent when provided`() {
        // Use named property access for this test
        val copyBundle = mockk<Bundle>(relaxed = true)
        val copyIntent = mockk<Intent>(relaxed = true).apply {
            every { extras } returns copyBundle
        }

        val result = DeepLinking.makeDeepLinkIntent(mockUri, mockContext, copyIntent)

        assertEquals(mockUri, result.data)
        assertEquals(Intent.ACTION_VIEW, result.action)
        assertEquals("com.test.app", result.`package`)
        assertEquals(Intent.FLAG_ACTIVITY_SINGLE_TOP, result.flags)
        verify { result.putExtras(copyBundle) }
    }

    @Test
    fun `makeDeepLinkIntent works without copyIntent`() {
        val result = DeepLinking.makeDeepLinkIntent(mockUri, mockContext, null)

        assertNotNull(result)
        assertEquals(mockUri, result.data)
        assertEquals(Intent.ACTION_VIEW, result.action)
    }

    /**
     * Mock a Uri with an already-normalized (lowercase) scheme, self-normalizing via
     * [Uri.normalizeScheme] the way a real normalized Uri would.
     */
    private fun mockUriWithScheme(scheme: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns scheme
        every { uri.normalizeScheme() } returns uri
        return uri
    }

    @Test
    fun `makeExternalIntent adds CATEGORY_BROWSABLE for https URI`() {
        MockIntent.setupIntentMocking()
        val categories = mutableListOf<String>()
        every {
            anyConstructed<Intent>().addCategory(capture(categories))
        } returns mockk(relaxed = true)

        DeepLinking.makeExternalIntent(mockUriWithScheme("https"))

        assertTrue(Intent.CATEGORY_BROWSABLE in categories)
    }

    @Test
    fun `makeExternalIntent adds CATEGORY_BROWSABLE for http URI`() {
        MockIntent.setupIntentMocking()
        val categories = mutableListOf<String>()
        every {
            anyConstructed<Intent>().addCategory(capture(categories))
        } returns mockk(relaxed = true)

        DeepLinking.makeExternalIntent(mockUriWithScheme("http"))

        assertTrue(Intent.CATEGORY_BROWSABLE in categories)
    }

    @Test
    fun `makeExternalIntent does not add CATEGORY_BROWSABLE for mailto URI`() {
        MockIntent.setupIntentMocking()
        val categories = mutableListOf<String>()
        every {
            anyConstructed<Intent>().addCategory(capture(categories))
        } returns mockk(relaxed = true)

        DeepLinking.makeExternalIntent(mockUriWithScheme("mailto"))

        assertFalse(Intent.CATEGORY_BROWSABLE in categories)
    }

    @Test
    fun `makeExternalIntent does not add CATEGORY_BROWSABLE for tel URI`() {
        MockIntent.setupIntentMocking()
        val categories = mutableListOf<String>()
        every {
            anyConstructed<Intent>().addCategory(capture(categories))
        } returns mockk(relaxed = true)

        DeepLinking.makeExternalIntent(mockUriWithScheme("tel"))

        assertFalse(Intent.CATEGORY_BROWSABLE in categories)
    }

    @Test
    fun `makeExternalIntent always adds FLAG_ACTIVITY_NEW_TASK`() {
        MockIntent.setupIntentMocking()
        val flagsSlot = mutableListOf<Int>()
        every {
            anyConstructed<Intent>().addFlags(capture(flagsSlot))
        } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().addCategory(any()) } returns mockk(relaxed = true)

        DeepLinking.makeExternalIntent(mockUriWithScheme("https"))
        DeepLinking.makeExternalIntent(mockUriWithScheme("mailto"))

        assertEquals(2, flagsSlot.size)
        assertTrue(flagsSlot.all { it and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
    }

    @Test
    fun `makeExternalIntent uses ACTION_DIAL for tel URI`() {
        MockIntent.setupIntentMocking()

        val result = DeepLinking.makeExternalIntent(mockUriWithScheme("tel"))

        assertEquals(Intent.ACTION_DIAL, result.action)
    }

    @Test
    fun `makeExternalIntent uses ACTION_SENDTO for mailto URI`() {
        MockIntent.setupIntentMocking()

        val result = DeepLinking.makeExternalIntent(mockUriWithScheme("mailto"))

        assertEquals(Intent.ACTION_SENDTO, result.action)
    }

    @Test
    fun `makeExternalIntent uses ACTION_SENDTO for sms URI`() {
        MockIntent.setupIntentMocking()

        val result = DeepLinking.makeExternalIntent(mockUriWithScheme("sms"))

        assertEquals(Intent.ACTION_SENDTO, result.action)
    }

    @Test
    fun `makeExternalIntent uses ACTION_SENDTO for smsto URI`() {
        MockIntent.setupIntentMocking()

        val result = DeepLinking.makeExternalIntent(mockUriWithScheme("smsto"))

        assertEquals(Intent.ACTION_SENDTO, result.action)
    }

    @Test
    fun `makeExternalIntent uses ACTION_VIEW for https URI`() {
        MockIntent.setupIntentMocking()
        every { anyConstructed<Intent>().addCategory(any()) } returns mockk(relaxed = true)

        val result = DeepLinking.makeExternalIntent(mockUriWithScheme("https"))

        assertEquals(Intent.ACTION_VIEW, result.action)
    }

    @Test
    fun `makeExternalIntent normalizes mixed-case scheme before dispatch`() {
        MockIntent.setupIntentMocking()
        val normalizedUri = mockUriWithScheme("mailto")
        val mixedCaseUri = mockk<Uri>(relaxed = true)
        every { mixedCaseUri.scheme } returns "MAILTO"
        every { mixedCaseUri.normalizeScheme() } returns normalizedUri

        val result = DeepLinking.makeExternalIntent(mixedCaseUri)

        assertEquals(Intent.ACTION_SENDTO, result.action)
        assertEquals(normalizedUri, result.data)
    }

    @Test
    fun `makeLaunchIntent returns configured intent when launch intent exists`() {
        val mockLaunchIntent = mockk<Intent>(relaxed = true)
        every { testPackageManager.getLaunchIntentForPackage("com.test.app") } returns mockLaunchIntent

        val result = DeepLinking.makeLaunchIntent(mockContext)

        assertNotNull(result)
        verify { mockLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
    }

    @Test
    fun `makeLaunchIntent adds extras when provided`() {
        val mockLaunchIntent = mockk<Intent>(relaxed = true)
        val testExtras = mockk<Bundle>(relaxed = true)
        every { testPackageManager.getLaunchIntentForPackage("com.test.app") } returns mockLaunchIntent

        val result = DeepLinking.makeLaunchIntent(mockContext, testExtras)

        assertNotNull(result)
        verify { mockLaunchIntent.putExtras(testExtras) }
        verify { mockLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
    }
}
