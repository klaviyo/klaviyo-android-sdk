package com.klaviyo.analytics.linking

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    }

    @Test
    fun `handleDeepLink broadcasts intent when no handler registered`() {
        every { testActivity.startActivity(any()) } returns Unit
        every { Registry.lifecycleMonitor.runWithCurrentOrNextActivity(any(), any()) } answers {
            val callback = secondArg<(Activity) -> Unit>()
            callback(testActivity)
            null
        }

        DeepLinking.handleDeepLink(mockUri)

        verify { testActivity.startActivity(any()) }
    }

    @Test
    fun `handleDeepLink sends no intent if link is unsupported`() {
        every { anyConstructed<Intent>().resolveActivity(any()) } returns null

        every { testActivity.startActivity(any()) } returns Unit
        every { Registry.lifecycleMonitor.runWithCurrentOrNextActivity(any(), any()) } answers {
            val callback = secondArg<(Activity) -> Unit>()
            callback(testActivity)
            null
        }

        DeepLinking.handleDeepLink(mockUri)

        verify(inverse = true) { testActivity.startActivity(any()) }
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
