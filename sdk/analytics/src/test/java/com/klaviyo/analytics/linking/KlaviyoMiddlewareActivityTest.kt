package com.klaviyo.analytics.linking

import android.content.Intent
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class KlaviyoMiddlewareActivityTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        mockkObject(Klaviyo)
        mockkObject(DeepLinking)
        every { DeepLinking.makeLaunchIntent(any(), any()) } returns mockk()
        every { DeepLinking.makeLaunchIntent(any()) } returns mockk()
        every { DeepLinking.sendLaunchIntent(any(), any()) } returns Unit
        every { DeepLinking.sendLaunchIntent(any()) } returns Unit
        every { DeepLinking.handleDeepLink(any()) } returns Unit
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(Klaviyo)
        unmockkObject(DeepLinking)
    }

    private fun createKlaviyoNotificationIntent(deepLinkUri: Uri? = null): Intent = MockIntent.setupIntentMocking().intent.apply {
        every { getStringExtra("com.klaviyo._k") } returns "some_klaviyo_data"
        every { data } returns deepLinkUri
        deepLinkUri?.apply {
            every { scheme } returns "https"
            every { path } returns "/some/path"
        }
    }

    private fun createKlaviyoUniversalTrackingIntent(): Intent = mockk<Intent>(relaxed = true).apply {
        val mockUri = mockk<Uri>().apply {
            every { scheme } returns "https"
            every { path } returns "/u/tracking123"
        }
        every { getStringExtra("com.klaviyo._k") } returns null
        every { data } returns mockUri
    }

    private fun createNonKlaviyoIntent(): Intent = mockk<Intent>(relaxed = true).apply {
        val mockUri = mockk<Uri>().apply {
            every { scheme } returns "https"
            every { path } returns ""
        }
        every { getStringExtra("com.klaviyo._k") } returns null
        every { data } returns mockUri
    }

    @Test
    fun `makeLaunchIntent creates properly configured intent`() {
        val mockUri = mockk<Uri>()

        val intent = KlaviyoMiddlewareActivity.makeLaunchIntent(mockContext, mockUri)

        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(mockUri, intent.data)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertEquals(mockContext.packageName, intent.`package`)
    }

    @Test
    fun `onNewIntent routes push notification intents correctly`() {
        val mockIntent = createKlaviyoNotificationIntent()

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify { Klaviyo.handlePush(mockIntent) }
        verify { DeepLinking.sendLaunchIntent(mockContext, any()) }
    }

    @Test
    fun `onNewIntent routes universal tracking intents correctly`() {
        val mockIntent = createKlaviyoUniversalTrackingIntent()

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify { Klaviyo.handleUniversalTrackingLink(mockIntent as Intent?) }
    }

    @Test
    fun `onNewIntent ignores non-Klaviyo intents`() {
        val mockIntent = createNonKlaviyoIntent()

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
        verify(exactly = 0) { Klaviyo.handleUniversalTrackingLink(any() as Intent?) }
    }

    @Test
    fun `onNewIntent handles null intent gracefully`() {
        KlaviyoMiddlewareActivity.onNewIntent(null, mockContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
        verify(exactly = 0) { Klaviyo.handleUniversalTrackingLink(any() as Intent?) }
    }

    @Test
    fun `onNewIntent with registered handler processes deep link via handler`() {
        val mockUri = mockk<Uri>()
        val mockIntent = createKlaviyoNotificationIntent(mockUri)
        every { DeepLinking.isHandlerRegistered } returns true

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify { Klaviyo.handlePush(mockIntent) }
        verify(exactly = 1) { DeepLinking.handleDeepLink(mockUri) }
    }

    @Test
    fun `onNewIntent with unregistered handler processes deep link via broadcast`() {
        val mockUri = mockk<Uri>()
        val mockIntent = createKlaviyoNotificationIntent(mockUri)
        every { DeepLinking.isHandlerRegistered } returns false

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify { Klaviyo.handlePush(mockIntent) }
        verify { DeepLinking.handleDeepLink(mockUri) }
    }

    @Test
    fun `onNewIntent launches host app when no deep link is present`() {
        val mockIntent = createKlaviyoNotificationIntent()

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify { Klaviyo.handlePush(mockIntent) }
        verify { DeepLinking.sendLaunchIntent(mockContext, any()) }
    }

    @Test
    fun `onNewIntent ignores non-notification intents`() {
        val mockIntent = createNonKlaviyoIntent()

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify(exactly = 0) { Klaviyo.handlePush(any()) }
        verify(exactly = 0) { DeepLinking.handleDeepLink(any()) }
        verify(exactly = 0) { DeepLinking.sendLaunchIntent(any(), any()) }
    }

    @Test
    fun `onNewIntent handles universal tracking intents`() {
        val mockIntent = createKlaviyoUniversalTrackingIntent()

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify { Klaviyo.handleUniversalTrackingLink(mockIntent as Intent?) }
    }

    @Test
    fun `onNewIntent ignores non-universal-tracking intents`() {
        val mockIntent = createNonKlaviyoIntent()

        KlaviyoMiddlewareActivity.onNewIntent(mockIntent, mockContext)

        verify(exactly = 0) { Klaviyo.handleUniversalTrackingLink(mockIntent as Intent?) }
    }
}
