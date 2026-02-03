package com.klaviyo.pushFcm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.BODY_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.TITLE_KEY
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.ActionButton
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
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
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk(relaxed = true)

        MockIntent.mockPendingIntent()

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        with(DeepLinking) {
            mockkObject(DeepLinking)
            every { makeLaunchIntent(any()) } returns mockk(relaxed = true)
            every { makeDeepLinkIntent(any(), any()) } returns mockk(relaxed = true)
        }
    }

    @After
    override fun cleanup() {
        MockIntent.unmockPendingIntent()
        unmockkStatic(Uri::class)
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

        verify { notification.buildNotification(mockContext) }
    }

    @Test
    fun `displayNotification builds and displays notification`() {
        val result = notification.displayNotification(mockContext)

        assertTrue(result)
        verify { notification.buildNotification(mockContext) }
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
}
