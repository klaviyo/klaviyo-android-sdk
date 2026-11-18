package com.klaviyo.pushFcm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants
import com.klaviyo.core.config.getManifestBoolean
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.BODY_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.TITLE_KEY
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.ActionButton
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCardAction
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.carouselCards
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.webUrl
import com.klaviyo.pushFcm.carousel.CarouselImageLoader
import com.klaviyo.pushFcm.carousel.CarouselStateStore
import com.klaviyo.pushFcm.carousel.KlaviyoCarouselReceiver
import com.klaviyo.push_fcm.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KlaviyoNotificationTest : BaseTest() {
    private val stubMessage = mutableMapOf(
        "_k" to "test_tracking_id",
        TITLE_KEY to "Test Title",
        BODY_KEY to "Test Body"
    )

    private var mockRemoteMessage = mockk<RemoteMessage>(relaxed = true).apply {
        every { data } returns stubMessage

        mockkObject(KlaviyoRemoteMessage)
        with(KlaviyoRemoteMessage) {
            every { isKlaviyoNotification } returns true
            every { channel_id } returns "test_channel"
            every { channel_name } returns "Test Channel"
            every { channel_description } returns "Test Description"
            every { channel_importance } returns NotificationManagerCompat.IMPORTANCE_DEFAULT
            every { title } returns "Test Title"
            every { body } returns "Test Body"
            every { deepLink } returns null
            every { imageUrl } returns null
            every { sound } returns null
            every { notificationCount } returns 0
            every { notificationPriority } returns NotificationCompat.PRIORITY_DEFAULT
            every { notificationTag } returns null
            every { getSmallIcon(any()) } returns android.R.drawable.ic_dialog_info
            every { getColor(any()) } returns null
        }
    }

    private var notification = spyk(KlaviyoNotification(mockRemoteMessage)).apply {
        every { hasNotificationPermission(any()) } returns true
    }

    private var mockNotificationManager = mockk<NotificationManagerCompat>(relaxed = true).apply {
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns this
    }

    @Before
    override fun setup() {
        super.setup()

        // Mock NotificationCompat.Builder constructor to return our mock builder
        mockkConstructor(NotificationCompat.Builder::class)
        every { anyConstructed<NotificationCompat.Builder>().setContentIntent(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setSmallIcon(any<Int>()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setColor(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentTitle(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentText(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setStyle(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setSound(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setNumber(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setPriority(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setAutoCancel(any()) } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().addAction(any<NotificationCompat.Action>())
        } answers { self as NotificationCompat.Builder }
        // Carousel-only builder calls (see CarouselNotificationBuilder); standard pushes never
        // reach them, so these stubs leave existing behaviour untouched.
        every {
            anyConstructed<NotificationCompat.Builder>().setCustomContentView(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setCustomBigContentView(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setOnlyAlertOnce(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setDeleteIntent(any())
        } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk(relaxed = true)

        MockIntent.mockPendingIntent()

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        with(DeepLinking) {
            mockkObject(DeepLinking)
            every { makeLaunchIntent(any()) } returns mockk(relaxed = true)
            every { makeDeepLinkIntent(any(), any()) } returns mockk(relaxed = true)
            every { makeExternalIntent(any()) } returns mockk(relaxed = true)
        }

        // Mock makeIntent to avoid constructing a real Intent. This test class doesn't call
        // MockIntent.setupIntentMocking(), so an `Intent()` no-arg call inside makeIntent would
        // hit the Android stub and throw "Stub! Not implemented." Per-test specs override this
        // default to capture the URL passed through.
        mockkObject(KlaviyoTrampolineActivity)
        every {
            KlaviyoTrampolineActivity.forBrowserUrl(any(), any())
        } returns mockk(relaxed = true)
        every {
            KlaviyoTrampolineActivity.forDestination(any(), any())
        } returns mockk(relaxed = true)
        // buildNotification reads the auto-tracking flag from the build-time context via the
        // Context.getManifestBoolean extension. Default to off (return the passed default);
        // flag-on tests override via enableAutomaticPushOpenTracking().
        mockkStatic("com.klaviyo.core.config.KlaviyoConfigKt")
        // Extension fn: receiver is the first arg, so the default value is the third arg.
        every { mockContext.getManifestBoolean(any(), any()) } answers { thirdArg() }

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.webUrl } returns null
        }
    }

    @After
    override fun cleanup() {
        MockIntent.unmockPendingIntent()
        unmockkStatic(Uri::class)
        unmockkStatic("com.klaviyo.core.config.KlaviyoConfigKt")
        super.cleanup()
    }

    @Test
    fun `displayNotification returns false when not a Klaviyo notification`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.isKlaviyoNotification } returns false
        }

        val result = notification.displayNotification(mockContext)

        assertFalse(result)
        verify(exactly = 0) { mockNotificationManager.notify(any<String>(), any(), any()) }
    }

    @Test
    fun `displayNotification returns false when notification permission not granted`() {
        every { notification.hasNotificationPermission(any()) } returns false

        val result = notification.displayNotification(mockContext)

        assertFalse(result)
        verify(exactly = 0) { mockNotificationManager.notify(any<String>(), any(), any()) }
    }

    @Test
    fun `displayNotification creates notification channel`() {
        notification.displayNotification(mockContext)
        verify { mockNotificationManager.createNotificationChannel(any<NotificationChannelCompat>()) }
    }

    @Test
    fun `displayNotification calls buildNotification with context`() {
        notification.displayNotification(mockContext)

        verify { notification.buildNotification(mockContext, any()) }
    }

    @Test
    fun `displayNotification builds and displays notification`() {
        val result = notification.displayNotification(mockContext)

        assertTrue(result)
        verify { notification.buildNotification(mockContext, any()) }
        verify { mockNotificationManager.notify(any<String>(), eq(0), any()) }
    }

    @Test
    fun `displayNotification uses custom notification tag when provided`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.notificationTag } returns "custom_tag"
        }

        notification.displayNotification(mockContext)

        verify { mockNotificationManager.notify(eq("custom_tag"), eq(0), any()) }
    }

    @Test
    fun `displayNotification generates tag when not provided`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.notificationTag } returns null
        }

        notification.displayNotification(mockContext)

        // Should still call notify with some string tag (generated from timestamp)
        verify { mockNotificationManager.notify(any<String>(), eq(0), any()) }
    }

    @Test
    fun `buildNotification sets title and body from message`() {
        notification.displayNotification(mockContext)

        verify { anyConstructed<NotificationCompat.Builder>().setContentTitle("Test Title") }
        verify { anyConstructed<NotificationCompat.Builder>().setContentText("Test Body") }
    }

    @Test
    fun `buildNotification sets small icon from message`() {
        notification.displayNotification(mockContext)

        verify {
            anyConstructed<NotificationCompat.Builder>().setSmallIcon(
                android.R.drawable.ic_dialog_info
            )
        }
    }

    @Test
    fun `buildNotification sets color when provided`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.getColor(any()) } returns 0xFF0000
        }

        notification.displayNotification(mockContext)

        verify { anyConstructed<NotificationCompat.Builder>().setColor(0xFF0000) }
    }

    @Test
    fun `buildNotification does not set color when null`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.getColor(any()) } returns null
        }

        notification.displayNotification(mockContext)

        verify(exactly = 0) { anyConstructed<NotificationCompat.Builder>().setColor(any()) }
    }

    @Test
    fun `buildNotification sets notification count`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.notificationCount } returns 5
        }

        notification.displayNotification(mockContext)

        verify { anyConstructed<NotificationCompat.Builder>().setNumber(5) }
    }

    @Test
    fun `buildNotification sets priority from message`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.notificationPriority } returns NotificationCompat.PRIORITY_HIGH
        }

        notification.displayNotification(mockContext)

        verify {
            anyConstructed<NotificationCompat.Builder>().setPriority(
                NotificationCompat.PRIORITY_HIGH
            )
        }
    }

    @Test
    fun `buildNotification sets autoCancel to true`() {
        notification.displayNotification(mockContext)

        verify { anyConstructed<NotificationCompat.Builder>().setAutoCancel(true) }
    }

    @Test
    fun `buildNotification sets BigTextStyle with body`() {
        notification.displayNotification(mockContext)

        verify { anyConstructed<NotificationCompat.Builder>().setStyle(any()) }
    }

    @Test
    fun `notification with deep link creates ACTION_VIEW intent`() {
        val mockDeepLinkUri = mockk<Uri>(relaxed = true)
        val mockDeepLinkIntent = mockk<Intent>(relaxed = true)
        val intentSlot = slot<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.deepLink } returns mockDeepLinkUri
        }

        every { DeepLinking.makeDeepLinkIntent(mockDeepLinkUri, any()) } returns mockDeepLinkIntent
        every { mockDeepLinkIntent.resolveActivity(any()) } returns mockk() // Intent is supported

        every {
            PendingIntent.getActivity(any(), any(), capture(intentSlot), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { DeepLinking.makeDeepLinkIntent(mockDeepLinkUri, mockContext) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any()) }
        assertEquals(intentSlot.captured, mockDeepLinkIntent)
    }

    @Test
    fun `notification with unsupported deep link falls back to launch intent`() {
        val mockDeepLinkUri = mockk<Uri>(relaxed = true)
        val mockDeepLinkIntent = mockk<Intent>(relaxed = true)
        val mockLaunchIntent = mockk<Intent>(relaxed = true)
        val intentSlot = slot<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.deepLink } returns mockDeepLinkUri
        }

        every { DeepLinking.makeDeepLinkIntent(mockDeepLinkUri, any()) } returns mockDeepLinkIntent
        every { mockDeepLinkIntent.resolveActivity(any()) } returns null // Intent is NOT supported
        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), capture(intentSlot), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { DeepLinking.makeDeepLinkIntent(mockDeepLinkUri, mockContext) }
        verify { DeepLinking.makeLaunchIntent(mockContext) }
        assertEquals(intentSlot.captured, mockLaunchIntent)
    }

    @Test
    fun `notification without deep link creates launch intent`() {
        val mockLaunchIntent = mockk<Intent>(relaxed = true)
        val intentSlot = slot<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.deepLink } returns null
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), capture(intentSlot), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { DeepLinking.makeLaunchIntent(mockContext) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any()) }
        assertEquals(intentSlot.captured, mockLaunchIntent)
    }

    @Test
    fun `action buttons with unsupported deep links fall back to launch intent`() {
        val mockDeepLinkIntent = mockk<Intent>(relaxed = true)
        val mockLaunchIntent = mockk<Intent>(relaxed = true)
        val intents = mutableListOf<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.DeepLink(
                    id = "deep-link",
                    label = "Open deep link",
                    url = "app://invalid"
                ),
                ActionButton.OpenApp(
                    id = "open-app",
                    label = "Open app"
                )
            )
        }

        every { DeepLinking.makeDeepLinkIntent(any(), any()) } returns mockDeepLinkIntent
        every { mockDeepLinkIntent.resolveActivity(any()) } returns null
        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } answers {
            intents.add(invocation.args[2] as Intent)
            mockk(relaxed = true)
        }

        notification.displayNotification(mockContext)

        assertEquals(3, intents.size)
        assertEquals(mockLaunchIntent, intents[1])
        assertEquals(mockLaunchIntent, intents[2])
    }

    @Test
    fun `action button open app without url uses launch intent`() {
        val mockLaunchIntent = mockk<Intent>(relaxed = true)
        val intents = mutableListOf<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenApp(
                    id = "open-app",
                    label = "Open app"
                )
            )
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } answers {
            intents.add(invocation.args[2] as Intent)
            mockk(relaxed = true)
        }

        notification.displayNotification(mockContext)

        assertEquals(2, intents.size)
        assertEquals(mockLaunchIntent, intents.last())
    }

    @Test
    fun `opened intent adds CLEAR_TOP flag for launch intent`() {
        val mockLaunchIntent = mockk<Intent>(relaxed = true)
        val intentSlot = slot<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.deepLink } returns null
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), capture(intentSlot), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { mockLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
    }

    @Test
    fun `opened intent adds CLEAR_TOP flag for deep link intent`() {
        val mockDeepLinkUri = mockk<Uri>(relaxed = true)
        val mockDeepLinkIntent = mockk<Intent>(relaxed = true)

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.deepLink } returns mockDeepLinkUri
        }

        every { DeepLinking.makeDeepLinkIntent(mockDeepLinkUri, any()) } returns mockDeepLinkIntent
        every { mockDeepLinkIntent.resolveActivity(any()) } returns mockk()

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { mockDeepLinkIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
    }

    @Test
    fun `pending intent created with correct flags`() {
        val flagsSlot = slot<Int>()

        every {
            PendingIntent.getActivity(any(), any(), any(), capture(flagsSlot))
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        val expectedFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        assertEquals(expectedFlags, flagsSlot.captured)
    }

    @Test
    fun `pending intent includes context and intent`() {
        val contextSlot = slot<Context>()
        val intentSlot = slot<Intent>()
        val mockPendingIntent = mockk<PendingIntent>(relaxed = true)

        every {
            PendingIntent.getActivity(
                capture(contextSlot),
                any(),
                capture(intentSlot),
                any()
            )
        } returns mockPendingIntent

        notification.displayNotification(mockContext)

        assertEquals(mockContext, contextSlot.captured)
        verify { anyConstructed<NotificationCompat.Builder>().setContentIntent(mockPendingIntent) }
        verify { intentSlot.captured.putExtra("com.klaviyo._k", "test_tracking_id") }
        verify { intentSlot.captured.putExtra("com.klaviyo.title", "Test Title") }
    }

    @Test
    fun `action button without launch intent does not add action`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenApp(
                    id = "open",
                    label = "Open"
                )
            )
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns null

        notification.displayNotification(mockContext)

        verify(exactly = 0) {
            anyConstructed<NotificationCompat.Builder>().addAction(any<NotificationCompat.Action>())
        }
    }

    @Test
    fun `action buttons use unique request codes`() {
        val requestCodes = mutableListOf<Int>()

        every {
            anyConstructed<NotificationCompat.Builder>().addAction(any<NotificationCompat.Action>())
        } answers { self as NotificationCompat.Builder }

        every {
            PendingIntent.getActivity(any(), capture(requestCodes), any(), any())
        } returns mockk(relaxed = true)

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.DeepLink(
                    id = "first",
                    label = "First",
                    url = "https://example.com/first"
                ),
                ActionButton.OpenApp(
                    id = "second",
                    label = "Second"
                )
            )
        }

        notification.displayNotification(mockContext)

        val actionRequestCodes = requestCodes.drop(1)
        assertEquals(2, actionRequestCodes.size)
        assertEquals(actionRequestCodes.size, actionRequestCodes.distinct().size)
        assertTrue(actionRequestCodes.none { it == 1000 || it == 1001 })
    }

    @Test
    fun `content intent request code does not collide with action button`() {
        val requestCodes = mutableListOf<Int>()
        val mockLaunchIntent = mockk<Intent>(relaxed = true)

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenApp(
                    id = "open",
                    label = "Open app"
                )
            )
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), capture(requestCodes), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        assertEquals(2, requestCodes.size)
        assertTrue(requestCodes.first() != requestCodes.last())
    }

    @Test
    fun `action button intent includes tracking extras for OpenApp button`() {
        val mockLaunchIntent = spyk<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenApp(
                    id = "com.klaviyo.test.open",
                    label = "Open App"
                )
            )
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent
        every { mockLaunchIntent.putExtra(any<String>(), any<String>()) } returns mockLaunchIntent
        every { mockLaunchIntent.addFlags(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { mockLaunchIntent.putExtra("com.klaviyo.Button Label", "Open App") }
        verify { mockLaunchIntent.putExtra("com.klaviyo.Button Action", "Open App") }
        verify(exactly = 0) {
            mockLaunchIntent.putExtra(
                "com.klaviyo.Button Link",
                any<String>()
            )
        }
    }

    @Test
    fun `action button intent includes tracking extras for DeepLink button`() {
        val mockDeepLinkIntent = spyk<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.DeepLink(
                    id = "com.klaviyo.test.view",
                    label = "View Order",
                    url = "klaviyotest://order/123"
                )
            )
        }

        every { DeepLinking.makeDeepLinkIntent(any(), any()) } returns mockDeepLinkIntent
        every { mockDeepLinkIntent.resolveActivity(any()) } returns mockk()
        every { mockDeepLinkIntent.putExtra(any<String>(), any<String>()) } returns mockDeepLinkIntent
        every { mockDeepLinkIntent.addFlags(any()) } returns mockDeepLinkIntent

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { mockDeepLinkIntent.putExtra("com.klaviyo.Button Label", "View Order") }
        verify { mockDeepLinkIntent.putExtra("com.klaviyo.Button Action", "Deep Link") }
        verify {
            mockDeepLinkIntent.putExtra(
                "com.klaviyo.Button Link",
                "klaviyotest://order/123"
            )
        }
    }

    @Test
    fun `action button intent includes notification tag for dismissal`() {
        val mockLaunchIntent = spyk<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.notificationTag } returns "test_tag"
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenApp(
                    id = "open",
                    label = "Open"
                )
            )
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent
        every { mockLaunchIntent.putExtra(any<String>(), any<String>()) } returns mockLaunchIntent
        every { mockLaunchIntent.addFlags(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify {
            mockLaunchIntent.putExtra(
                "_klaviyo.notification_tag",
                "test_tag"
            )
        }
    }

    @Test
    fun `action button intent includes generated tag when no notification tag in payload`() {
        val mockLaunchIntent = spyk<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.notificationTag } returns null
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenApp(
                    id = "open",
                    label = "Open"
                )
            )
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent
        every { mockLaunchIntent.putExtra(any<String>(), any<String>()) } returns mockLaunchIntent
        every { mockLaunchIntent.addFlags(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        // Should still have a notification tag extra (generated from timestamp)
        verify {
            mockLaunchIntent.putExtra(
                "_klaviyo.notification_tag",
                any<String>()
            )
        }
    }

    @Test
    fun `open_url tap routes through trampoline activity with browser URL`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.webUrl } returns "https://example.com"
            every { mockRemoteMessage.deepLink } returns null
        }

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        // Body tap PendingIntent targets the trampoline with the URL passed through,
        // not the browser directly.
        verify {
            KlaviyoTrampolineActivity.forBrowserUrl(mockContext, "https://example.com")
        }
        verify(exactly = 0) { DeepLinking.makeExternalIntent(any()) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any()) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any()) }
    }

    @Test
    fun `tap with both deepLink and webUrl prefers deepLink and warns`() {
        val mockDeepLinkUri = mockk<Uri>(relaxed = true)
        val mockDeepLinkIntent = mockk<Intent>(relaxed = true).apply {
            every { resolveActivity(any()) } returns mockk()
        }

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.webUrl } returns "https://example.com"
            every { mockRemoteMessage.deepLink } returns mockDeepLinkUri
        }

        every { DeepLinking.makeDeepLinkIntent(any(), any()) } returns mockDeepLinkIntent

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        // Deep link wins; trampoline is never invoked despite webUrl being set.
        verify { DeepLinking.makeDeepLinkIntent(eq(mockDeepLinkUri), any()) }
        verify(exactly = 0) { KlaviyoTrampolineActivity.forBrowserUrl(any(), any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `tap with no webUrl and no deepLink falls back to launch intent with CLEAR_TOP`() {
        val mockLaunchIntent = mockk<Intent>(relaxed = true)
        val intentSlot = slot<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.webUrl } returns null
            every { mockRemoteMessage.deepLink } returns null
        }

        every { DeepLinking.makeLaunchIntent(any()) } returns mockLaunchIntent

        every {
            PendingIntent.getActivity(any(), any(), capture(intentSlot), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { DeepLinking.makeLaunchIntent(mockContext) }
        verify(exactly = 0) { DeepLinking.makeExternalIntent(any()) }
        verify { mockLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        assertEquals(mockLaunchIntent, intentSlot.captured)
    }

    @Test
    fun `open_url action button routes through trampoline activity with browser URL`() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenUrl(
                    id = "open-url",
                    label = "Open Website",
                    url = "https://example.com"
                )
            )
        }

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify {
            KlaviyoTrampolineActivity.forBrowserUrl(mockContext, "https://example.com")
        }
        verify(exactly = 0) { DeepLinking.makeExternalIntent(any()) }
    }

    @Test
    fun `open_url action button intent includes tracking extras`() {
        val mockTrampolineIntent = mockk<Intent>(relaxed = true)
        every {
            KlaviyoTrampolineActivity.forBrowserUrl(any(), any())
        } returns mockTrampolineIntent

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenUrl(
                    id = "open-url",
                    label = "Open Website",
                    url = "https://example.com"
                )
            )
        }

        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { mockTrampolineIntent.putExtra("com.klaviyo.Button Label", "Open Website") }
        verify { mockTrampolineIntent.putExtra("com.klaviyo.Button Action", "Open URL") }
        verify { mockTrampolineIntent.putExtra("com.klaviyo.Button Link", "https://example.com") }
    }

    /** Opt into automatic push tracking for the duration of a test. */
    private fun enableAutomaticPushOpenTracking() {
        // Overrides the flag-off default stubbed in setup(); buildNotification reads this off the
        // build-time context (not Registry.config), so it works pre-init.
        every {
            mockContext.getManifestBoolean(
                KlaviyoPushService.METADATA_AUTOMATIC_PUSH_OPEN_TRACKING,
                false
            )
        } returns true
    }

    @Test
    fun `automatic tracking on - body deep link tap routes through trampoline`() {
        enableAutomaticPushOpenTracking()
        val mockDeepLinkUri = mockk<Uri>(relaxed = true)
        val mockTrampolineIntent = mockk<Intent>(relaxed = true)
        val intentSlot = slot<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.deepLink } returns mockDeepLinkUri
        }
        every { KlaviyoTrampolineActivity.forDestination(mockContext, mockDeepLinkUri) } returns mockTrampolineIntent
        every {
            PendingIntent.getActivity(any(), any(), capture(intentSlot), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { KlaviyoTrampolineActivity.forDestination(mockContext, mockDeepLinkUri) }
        // Deep link must NOT target the host directly when auto-tracking is on.
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any()) }
        assertEquals(mockTrampolineIntent, intentSlot.captured)
        // Extras parity: the trampoline intent still carries the Klaviyo extras.
        verify { mockTrampolineIntent.putExtra("com.klaviyo._k", "test_tracking_id") }
        verify { mockTrampolineIntent.putExtra("com.klaviyo.title", "Test Title") }
    }

    @Test
    fun `automatic tracking on - plain body tap routes through trampoline launch`() {
        enableAutomaticPushOpenTracking()
        val mockTrampolineIntent = mockk<Intent>(relaxed = true)
        val intentSlot = slot<Intent>()

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.deepLink } returns null
            every { mockRemoteMessage.webUrl } returns null
        }
        every { KlaviyoTrampolineActivity.forDestination(mockContext, null) } returns mockTrampolineIntent
        every {
            PendingIntent.getActivity(any(), any(), capture(intentSlot), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { KlaviyoTrampolineActivity.forDestination(mockContext, null) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any()) }
        assertEquals(mockTrampolineIntent, intentSlot.captured)
    }

    @Test
    fun `automatic tracking on - deep_link action button routes through trampoline with extras`() {
        enableAutomaticPushOpenTracking()
        val parsedUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("klaviyotest://order/123") } returns parsedUri
        val mockButtonIntent = mockk<Intent>(relaxed = true)
        every { KlaviyoTrampolineActivity.forDestination(mockContext, parsedUri) } returns mockButtonIntent

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.notificationTag } returns "test_tag"
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.DeepLink(
                    id = "com.klaviyo.test.view",
                    label = "View Order",
                    url = "klaviyotest://order/123"
                )
            )
        }
        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        // Routed through the trampoline, not a direct ACTION_VIEW…
        verify { KlaviyoTrampolineActivity.forDestination(mockContext, parsedUri) }
        verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any()) }
        // …carrying the same extras the non-trampoline button intent does today.
        verify { mockButtonIntent.putExtra("_klaviyo.notification_tag", "test_tag") }
        verify { mockButtonIntent.putExtra("com.klaviyo._k", "test_tracking_id") }
        verify { mockButtonIntent.putExtra("com.klaviyo.Button Label", "View Order") }
        verify { mockButtonIntent.putExtra("com.klaviyo.Button Link", "klaviyotest://order/123") }
    }

    @Test
    fun `automatic tracking on - open_app action button routes through trampoline with extras`() {
        enableAutomaticPushOpenTracking()
        val mockButtonIntent = mockk<Intent>(relaxed = true)
        every { KlaviyoTrampolineActivity.forDestination(mockContext, null) } returns mockButtonIntent

        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.notificationTag } returns "test_tag"
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.OpenApp(id = "open", label = "Open")
            )
        }
        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        verify { KlaviyoTrampolineActivity.forDestination(mockContext, null) }
        verify(exactly = 0) { DeepLinking.makeLaunchIntent(any()) }
        // Extras parity: notification tag (for dismissal) + Klaviyo extras still applied.
        verify { mockButtonIntent.putExtra("_klaviyo.notification_tag", "test_tag") }
        verify { mockButtonIntent.putExtra("com.klaviyo._k", "test_tracking_id") }
    }

    @Test
    fun `open_url tap targets trampoline even with automatic tracking on`() {
        enableAutomaticPushOpenTracking()
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.webUrl } returns "https://example.com"
            every { mockRemoteMessage.deepLink } returns null
        }
        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        // open_url always uses the browser-URL trampoline factory, never the deep-link/launch one.
        verify { KlaviyoTrampolineActivity.forBrowserUrl(mockContext, "https://example.com") }
        verify(exactly = 0) { KlaviyoTrampolineActivity.forDestination(any(), any()) }
    }

    @Test
    fun `stamps a dedup uid on the body intent even when auto-tracking is off`() {
        // Non-trampoline path: the host receives the intent directly, but it must still carry a
        // per-notification dedup uid so handlePush can dedupe tm-less opens (e.g. a host calling
        // handlePush twice for one tap).
        val mockBodyIntent = mockk<Intent>(relaxed = true)
        every { DeepLinking.makeLaunchIntent(mockContext) } returns mockBodyIntent
        val intentSlot = slot<Intent>()
        every {
            PendingIntent.getActivity(any(), any(), capture(intentSlot), any())
        } returns mockk(relaxed = true)

        notification.displayNotification(mockContext)

        assertEquals(mockBodyIntent, intentSlot.captured)
        verify { mockBodyIntent.putExtra("_klaviyo.notification_uid", any<String>()) }
    }

    @Test
    fun `body and action button share one dedup uid per notification`() {
        // The dedup key must be per-notification, not per-intent: body and every action button
        // carry the same uid so distinct targets on one notification collapse to a single open.
        enableAutomaticPushOpenTracking()
        val bodyUri = mockk<Uri>(relaxed = true)
        val buttonUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("klaviyotest://order/123") } returns buttonUri
        val mockBodyIntent = mockk<Intent>(relaxed = true)
        val mockButtonIntent = mockk<Intent>(relaxed = true)
        val bodyUid = slot<String>()
        val buttonUid = slot<String>()
        every {
            mockBodyIntent.putExtra("_klaviyo.notification_uid", capture(bodyUid))
        } returns mockBodyIntent
        every {
            mockButtonIntent.putExtra("_klaviyo.notification_uid", capture(buttonUid))
        } returns mockButtonIntent
        every { KlaviyoTrampolineActivity.forDestination(mockContext, bodyUri) } returns mockBodyIntent
        every { KlaviyoTrampolineActivity.forDestination(mockContext, buttonUri) } returns mockButtonIntent
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk(
            relaxed = true
        )
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.deepLink } returns bodyUri
            every { mockRemoteMessage.actionButtons } returns listOf(
                ActionButton.DeepLink(
                    id = "com.klaviyo.test.view",
                    label = "View Order",
                    url = "klaviyotest://order/123"
                )
            )
        }

        notification.displayNotification(mockContext)

        assertTrue("body intent should carry a dedup uid", bodyUid.isCaptured)
        assertTrue("button intent should carry a dedup uid", buttonUid.isCaptured)
        assertTrue("dedup uid should be non-empty", bodyUid.captured.isNotEmpty())
        assertEquals(
            "body and button should share one per-notification uid",
            bodyUid.captured,
            buttonUid.captured
        )
    }

    // ---------------------------------------------------------------------------------------
    // Carousel pushes (PUSH-1938)
    // ---------------------------------------------------------------------------------------

    private companion object {
        const val TEST_TAG = "test_tag"
        const val TEST_UID = "test-notification-uid"
        const val TEST_REQUEST_CODE_BASE = 1000
        const val CAROUSEL_SIZE = 4
    }

    /**
     * Parsed cards for a 4-card carousel, with distinct titles and descriptions so assertions can
     * tell which card is being rendered.
     */
    private val carouselCards = (1..CAROUSEL_SIZE).map {
        CarouselCard(
            position = it,
            imageUrl = mockk(relaxed = true),
            title = "Card $it title",
            description = "Card $it description",
            action = CarouselCardAction.OpenApp
        )
    }

    /**
     * Install the shared carousel setup: parsed cards on the message, a cached bitmap from the
     * image loader, the store writes, RemoteViews constructor mocking, the counter string and the
     * Intent/Bundle constructor mocking the carousel's broadcast intents need.
     */
    @Suppress("LongMethod")
    private fun withCarouselPush() {
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.carouselCards } returns carouselCards
        }
        mockkObject(CarouselImageLoader)
        every { CarouselImageLoader.get(any(), any()) } returns mockk(relaxed = true)
        every { CarouselImageLoader.loadAll(any(), any()) } returns emptyMap()
        // displayNotification evicts a tag's previously cached bitmaps before reloading, so a
        // re-sent push under a reused tag never serves a stale image.
        every { CarouselImageLoader.evict(any()) } returns Unit
        mockkObject(CarouselStateStore)
        every { CarouselStateStore.setIndex(any(), any(), any()) } returns Unit

        // RemoteViews is an Android stub on the JVM; capture the text/bitmap sets through a mock.
        mockkConstructor(RemoteViews::class)
        every {
            anyConstructed<RemoteViews>().setTextViewText(any(), any<String>())
        } returns Unit
        every { anyConstructed<RemoteViews>().setImageViewBitmap(any(), any()) } returns Unit
        every { anyConstructed<RemoteViews>().setViewVisibility(any(), any()) } returns Unit
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } returns Unit

        // The counter is built with getString(id, index + 1, size).
        every { mockContext.getString(any(), *anyVararg()) } answers {
            val args = invocation.args[1] as Array<*>
            "${args[0]} / ${args[1]}"
        }

        // Arrow/delete intents are built with a no-arg Intent() + setters; the JVM stub throws.
        mockkConstructor(Intent::class)
        every {
            anyConstructed<Intent>().setClassName(any<String>(), any<String>())
        } answers { self as Intent }
        every { anyConstructed<Intent>().setAction(any()) } answers { self as Intent }
        every {
            anyConstructed<Intent>().putExtra(any<String>(), any<String>())
        } answers { self as Intent }
        every {
            anyConstructed<Intent>().putExtra(any<String>(), any<Int>())
        } answers { self as Intent }
        every {
            anyConstructed<Intent>().putExtra(any<String>(), any<Bundle>())
        } answers { self as Intent }
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putString(any(), any()) } returns Unit
    }

    /** Tear down the constructor mocks installed by [withCarouselPush]. */
    private fun withoutCarouselPush() {
        unmockkConstructor(Bundle::class)
        unmockkConstructor(Intent::class)
        unmockkConstructor(RemoteViews::class)
        unmockkObject(CarouselStateStore)
        unmockkObject(CarouselImageLoader)
    }

    @Test
    fun `carousel sets DecoratedCustomViewStyle and not BigTextStyle`() {
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            verify {
                anyConstructed<NotificationCompat.Builder>()
                    .setStyle(match { it is NotificationCompat.DecoratedCustomViewStyle })
            }
            verify(exactly = 0) {
                anyConstructed<NotificationCompat.Builder>()
                    .setStyle(match { it is NotificationCompat.BigTextStyle })
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel sets both custom content views`() {
        // AC-1: the collapsed and expanded RemoteViews are attached to the builder.
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            verify { anyConstructed<NotificationCompat.Builder>().setCustomContentView(any()) }
            verify { anyConstructed<NotificationCompat.Builder>().setCustomBigContentView(any()) }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel shows the current card image, title and description`() {
        // AC-1: the expanded view carries card 2's image, title and description when index = 1.
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID,
                carouselIndex = 1
            )

            verify {
                anyConstructed<RemoteViews>()
                    .setImageViewBitmap(R.id.klaviyo_carousel_image, any())
            }
            verify {
                anyConstructed<RemoteViews>()
                    .setTextViewText(R.id.klaviyo_carousel_title, "Card 2 title")
            }
            verify {
                anyConstructed<RemoteViews>()
                    .setTextViewText(R.id.klaviyo_carousel_description, "Card 2 description")
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel counter shows the one-based card position`() {
        // AC-1: "1 / 4" for the first card of four.
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID,
                carouselIndex = 0
            )

            verify {
                anyConstructed<RemoteViews>().setTextViewText(
                    R.id.klaviyo_carousel_counter,
                    "1 / 4"
                )
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel hides the image when the card bitmap is not cached`() {
        withCarouselPush()
        try {
            every { CarouselImageLoader.get(any(), any()) } returns null

            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            verify {
                anyConstructed<RemoteViews>()
                    .setViewVisibility(R.id.klaviyo_carousel_image, View.GONE)
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel shows the current card title in the collapsed view`() {
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID,
                carouselIndex = 2
            )

            verify {
                anyConstructed<RemoteViews>()
                    .setTextViewText(R.id.klaviyo_carousel_collapsed_title, "Card 3 title")
            }
            verify {
                anyConstructed<RemoteViews>()
                    .setTextViewText(R.id.klaviyo_carousel_collapsed_counter, "3 / 4")
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel body tap targets the current card's action`() {
        // AC-2 (body half): the content intent for card 3 uses that card's own action/url — not
        // the notification-level deep link — and carries the card's 1-based position.
        withCarouselPush()
        try {
            val browserUrlSlot = slot<String>()
            val deepLinkUri = mockk<Uri>(relaxed = true)
            val openUrl = mockk<Uri>(relaxed = true)
            every { openUrl.toString() } returns "https://card-3.example.com"
            val cards = listOf(
                CarouselCard(
                    1,
                    mockk(relaxed = true),
                    "Card 1 title",
                    "d",
                    CarouselCardAction.OpenApp
                ),
                CarouselCard(
                    2,
                    mockk(relaxed = true),
                    "Card 2 title",
                    "d",
                    CarouselCardAction.OpenApp
                ),
                CarouselCard(
                    position = 3,
                    imageUrl = mockk(relaxed = true),
                    title = "Card 3 title",
                    description = "d",
                    action = CarouselCardAction.OpenUrl(openUrl)
                )
            )
            with(KlaviyoRemoteMessage) {
                every { mockRemoteMessage.carouselCards } returns cards
                // A notification-level deep link must be ignored for a carousel card tap.
                every { mockRemoteMessage.deepLink } returns deepLinkUri
            }
            every {
                KlaviyoTrampolineActivity.forBrowserUrl(any(), capture(browserUrlSlot))
            } returns mockk(relaxed = true)
            every {
                PendingIntent.getActivity(any(), any(), any(), any())
            } returns mockk(relaxed = true)

            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID,
                carouselIndex = 2
            )

            // Card 3 is an open_url card, so the tap goes to its own browser URL.
            assertEquals("https://card-3.example.com", browserUrlSlot.captured)
            verify(exactly = 0) { DeepLinking.makeDeepLinkIntent(any(), any()) }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel arrow taps go to the carousel receiver`() {
        // AC-2: prev and next are RemoteViews click targets bound to internal broadcasts.
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            verify {
                anyConstructed<RemoteViews>()
                    .setOnClickPendingIntent(eq(R.id.klaviyo_carousel_prev), any())
            }
            verify {
                anyConstructed<RemoteViews>()
                    .setOnClickPendingIntent(eq(R.id.klaviyo_carousel_next), any())
            }
            // Both arrows (and the delete intent) target the internal receiver by explicit component.
            verify(exactly = 3) {
                anyConstructed<Intent>().setClassName(
                    any<String>(),
                    eq(KlaviyoCarouselReceiver::class.java.name)
                )
            }
            verify { anyConstructed<Intent>().setAction(Constants.CAROUSEL_ACTION_PREV) }
            verify { anyConstructed<Intent>().setAction(Constants.CAROUSEL_ACTION_NEXT) }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel arrows are never rendered as action buttons`() {
        // AC-2 / AC-4 boundary: the three-button budget belongs to action_buttons.
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            // No action_buttons in the payload, so nothing should be added.
            verify(exactly = 0) {
                anyConstructed<NotificationCompat.Builder>()
                    .addAction(any<NotificationCompat.Action>())
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel broadcasts carry the tag, uid and request-code base`() {
        // AC-2: the receiver needs everything to re-post in place under the same tag.
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            verify { anyConstructed<Intent>().putExtra(Constants.NOTIFICATION_TAG_EXTRA, TEST_TAG) }
            verify {
                anyConstructed<Intent>().putExtra(Constants.NOTIFICATION_UID_EXTRA, TEST_UID)
            }
            verify {
                anyConstructed<Intent>()
                    .putExtra(Constants.CAROUSEL_REQUEST_CODE_BASE_EXTRA, TEST_REQUEST_CODE_BASE)
            }
            verify {
                anyConstructed<Intent>().putExtra(
                    Constants.CAROUSEL_SENDER_EXTRA,
                    any<String>()
                )
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel arrow and delete request codes are unique against content and buttons`() {
        withCarouselPush()
        try {
            with(KlaviyoRemoteMessage) {
                // The full three-button budget, so the offset range is checked end to end.
                every { mockRemoteMessage.actionButtons } returns listOf(
                    ActionButton.OpenApp(id = "one", label = "One"),
                    ActionButton.OpenApp(id = "two", label = "Two"),
                    ActionButton.OpenApp(id = "three", label = "Three")
                )
            }
            val broadcastCodes = mutableListOf<Int>()
            val activityCodes = mutableListOf<Int>()
            every {
                PendingIntent.getBroadcast(any(), capture(broadcastCodes), any(), any())
            } returns mockk(relaxed = true)
            every {
                PendingIntent.getActivity(any(), capture(activityCodes), any(), any())
            } returns mockk(relaxed = true)

            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            // prev, next and delete, each at its own offset.
            assertEquals(
                listOf(
                    TEST_REQUEST_CODE_BASE + Constants.CAROUSEL_PREV_OFFSET,
                    TEST_REQUEST_CODE_BASE + Constants.CAROUSEL_NEXT_OFFSET,
                    TEST_REQUEST_CODE_BASE + Constants.CAROUSEL_DELETE_OFFSET
                ),
                broadcastCodes
            )
            val allCodes = broadcastCodes + activityCodes
            assertEquals(
                "every request code must be unique",
                allCodes.size,
                allCodes.distinct().size
            )
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel arrow and delete intents use UPDATE_CURRENT and IMMUTABLE`() {
        withCarouselPush()
        try {
            val flags = mutableListOf<Int>()
            every {
                PendingIntent.getBroadcast(any(), any(), any(), capture(flags))
            } returns mockk(relaxed = true)

            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            assertEquals(3, flags.size)
            val expected = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            assertTrue(flags.all { it == expected })
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel sets a delete intent`() {
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            verify { anyConstructed<NotificationCompat.Builder>().setDeleteIntent(any()) }
            verify { anyConstructed<Intent>().setAction(Constants.CAROUSEL_ACTION_DELETE) }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel sets onlyAlertOnce`() {
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            verify { anyConstructed<NotificationCompat.Builder>().setOnlyAlertOnce(true) }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel still adds notification-level action buttons`() {
        // AC-4: action_buttons are notification-level and render on every card.
        withCarouselPush()
        try {
            with(KlaviyoRemoteMessage) {
                every { mockRemoteMessage.actionButtons } returns listOf(
                    ActionButton.OpenApp(id = "open", label = "Open"),
                    ActionButton.OpenApp(id = "view", label = "View")
                )
            }

            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID
            )

            verify(exactly = 2) {
                anyConstructed<NotificationCompat.Builder>()
                    .addAction(any<NotificationCompat.Action>())
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `standard push still sets BigTextStyle and never attaches custom views`() {
        // AC-5: standard pushes are unchanged.
        with(KlaviyoRemoteMessage) {
            every { mockRemoteMessage.carouselCards } returns null
        }

        notification.displayNotification(mockContext)

        verify { anyConstructed<NotificationCompat.Builder>().setStyle(any()) }
        verify(exactly = 0) {
            anyConstructed<NotificationCompat.Builder>().setStyle(
                match { it is NotificationCompat.DecoratedCustomViewStyle }
            )
        }
        verify(exactly = 0) {
            anyConstructed<NotificationCompat.Builder>().setCustomContentView(
                any()
            )
        }
        verify(exactly = 0) {
            anyConstructed<NotificationCompat.Builder>().setCustomBigContentView(any())
        }
        verify(exactly = 0) { anyConstructed<NotificationCompat.Builder>().setOnlyAlertOnce(any()) }
        verify(exactly = 0) { anyConstructed<NotificationCompat.Builder>().setDeleteIntent(any()) }
    }

    @Test
    fun `carousel display resets the stored index and preloads every card image`() {
        // AC-3: a re-sent push starts at card 1 and refreshes every card image. The evict must
        // come before the reload, or a reused tag could keep serving the previous carousel's
        // bitmaps for the new card positions.
        withCarouselPush()
        try {
            with(KlaviyoRemoteMessage) { every { mockRemoteMessage.notificationTag } returns TEST_TAG }

            notification.displayNotification(mockContext)

            verify { CarouselStateStore.setIndex(mockContext, TEST_TAG, 0) }
            verifyOrder {
                CarouselImageLoader.evict(TEST_TAG)
                CarouselImageLoader.loadAll(TEST_TAG, carouselCards)
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel display does not apply the standard rich image`() {
        withCarouselPush()
        try {
            with(KlaviyoRemoteMessage) {
                every { mockRemoteMessage.notificationTag } returns TEST_TAG
                every { mockRemoteMessage.imageUrl } returns mockk(relaxed = true)
            }

            notification.displayNotification(mockContext)

            // applyToNotification would set a BigPictureStyle; carousels use DecoratedCustomViewStyle.
            verify(exactly = 0) {
                anyConstructed<NotificationCompat.Builder>()
                    .setStyle(match { it is NotificationCompat.BigPictureStyle })
            }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `stale carousel body intent flags include UPDATE_CURRENT and carry the card position`() {
        // Regression for 7485c91d91: a re-post reuses the body request code, and a filterEquals
        // intent (extras not compared) would otherwise hand back the previously shown card's
        // token. UPDATE_CURRENT makes getActivity replace it with the new card's destination.
        withCarouselPush()
        try {
            val flagsSlot = slot<Int>()
            val requestCodeSlot = slot<Int>()
            val intentSlot = slot<Intent>()
            every {
                PendingIntent.getActivity(
                    any(),
                    capture(requestCodeSlot),
                    capture(intentSlot),
                    capture(flagsSlot)
                )
            } returns mockk(relaxed = true)

            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID,
                carouselIndex = 2
            )

            assertEquals(TEST_REQUEST_CODE_BASE, requestCodeSlot.captured)
            assertEquals(
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_ONE_SHOT or
                    PendingIntent.FLAG_UPDATE_CURRENT,
                flagsSlot.captured
            )
            // Card 3 is 1-based position 3.
            verify { intentSlot.captured.putExtra(Constants.CAROUSEL_POSITION_EXTRA, 3) }
        } finally {
            withoutCarouselPush()
        }
    }

    @Test
    fun `carousel clamps an out-of-range index to the last card`() {
        withCarouselPush()
        try {
            notification.buildNotification(
                mockContext,
                TEST_TAG,
                requestCodeBase = TEST_REQUEST_CODE_BASE,
                notificationUid = TEST_UID,
                carouselIndex = 99
            )

            verify {
                anyConstructed<RemoteViews>().setTextViewText(
                    R.id.klaviyo_carousel_counter,
                    "4 / 4"
                )
            }
        } finally {
            withoutCarouselPush()
        }
    }
}
