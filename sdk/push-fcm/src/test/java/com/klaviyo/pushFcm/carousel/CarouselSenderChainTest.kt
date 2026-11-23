package com.klaviyo.pushFcm.carousel

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.config.getManifestBoolean
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.KlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCardAction
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.actionButtons
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.carouselCards
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_id
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.deepLink
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.getColor
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.getSmallIcon
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationCount
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationPriority
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.sound
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.webUrl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the carousel arrow-tap sender.
 *
 * A carousel re-post rebuilds its `RemoteMessage` via `RemoteMessage.Builder(sender)`, but that
 * constructor sets the `to` field and `build()` removes any `"from"` key (verified in
 * firebase-messaging 23.4.0 bytecode), so the rebuilt message's `from` is always null. Before the
 * fix, the re-post therefore stamped `CAROUSEL_SENDER_EXTRA = null` on its own arrow intents, and
 * the *next* tap bailed out with "intent missing sender" — the carousel advanced once and froze.
 *
 * This runs a real 3-hop chain end to end: the first post's next-arrow intent, the receiver, that
 * re-post's own next-arrow intent, then the receiver again. It fails on the pre-fix code (the
 * second hop never re-posts) and passes once the receiver re-stamps the sender it received.
 */
internal class CarouselSenderChainTest : BaseTest() {
    private companion object {
        const val TAG = "carousel-tag"
        const val UID = "test-notification-uid"
        const val SENDER = "test-sender"
        const val REQUEST_CODE_BASE = 1000
    }

    /** One broadcast intent the builder assembled, captured at its `setClassName` boundary. */
    private class RecordedIntent {
        var action: String? = null
        val extras = mutableMapOf<String, Any?>()
    }

    private val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
    private val builtNotification = mockk<Notification>(relaxed = true)
    private val compatBuilder = mockk<NotificationCompat.Builder>(relaxed = true)

    private val cards = (1..3).map {
        CarouselCard(
            position = it,
            imageUrl = mockk(relaxed = true),
            title = "Title $it",
            description = "Description $it",
            action = CarouselCardAction.OpenApp
        )
    }

    /**
     * Every broadcast intent the builder assembles, in order. Each begins at a `setClassName` call,
     * because `mockkConstructor` hands every construction the same proxy — a single object whose
     * `action` field can't distinguish prev/next/delete — so the boundary is captured explicitly.
     */
    private val recorded = mutableListOf<RecordedIntent>()

    /** The record the constructed `Intent`'s getters currently read from. */
    private var active: RecordedIntent? = null

    /** Fired `notify` tags, so the number of re-posts is countable. */
    private val notifies = mutableListOf<String>()

    private var storedIndex = 0

    @Before
    override fun setup() {
        super.setup()

        stubCounterString()
        mockkObject(DeepLinking)
        every { DeepLinking.makeLaunchIntent(any()) } returns mockk(relaxed = true)
        every { DeepLinking.makeDeepLinkIntent(any(), any()) } returns mockk(relaxed = true)
        mockkStatic("com.klaviyo.core.config.KlaviyoConfigKt")
        // Extension fn: receiver is arg 1, so the default value is the third arg.
        every { mockContext.getManifestBoolean(any(), any()) } answers { thirdArg() }

        stubRemoteMessageAccessors()
        stubNotificationBuilder()
        stubRemoteViews()
        stubIntents()
        stubRemoteMessageBuilder()
        stubPendingIntents()

        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notificationManager
        every { notificationManager.areNotificationsEnabled() } returns true
        every { notificationManager.notify(any(), any(), any()) } answers {
            notifies.add(firstArg())
        }

        mockkObject(CarouselImageLoader)
        every { CarouselImageLoader.get(any(), any()) } returns mockk<Bitmap>(relaxed = true)
        every { CarouselImageLoader.thumbnail(any(), any()) } returns mockk<Bitmap>(relaxed = true)
        every { CarouselImageLoader.loadAll(any(), any()) } returns emptyMap()
        every { CarouselImageLoader.evict(any()) } returns Unit

        mockkObject(CarouselStateStore)
        every { CarouselStateStore.getIndex(any(), any(), any()) } answers { storedIndex }
        every { CarouselStateStore.advance(any(), any(), any(), any(), any(), any()) } answers {
            arg<() -> Unit>(5).invoke()
            storedIndex = arg(4)
            true
        }
        every { CarouselStateStore.clear(any(), any(), any()) } returns Unit
    }

    @After
    override fun cleanup() {
        unmockkObject(CarouselStateStore)
        unmockkObject(CarouselImageLoader)
        unmockkObject(DeepLinking)
        unmockkStatic(NotificationManagerCompat::class)
        unmockkStatic(PendingIntent::class)
        unmockkConstructor(RemoteMessage.Builder::class)
        unmockkConstructor(Intent::class)
        unmockkConstructor(RemoteViews::class)
        unmockkConstructor(NotificationCompat.Builder::class)
        unmockkConstructor(Bundle::class)
        unmockkObject(KlaviyoRemoteMessage)
        unmockkStatic("com.klaviyo.core.config.KlaviyoConfigKt")
        unmockkStatic(TextUtils::class)
        super.cleanup()
    }

    @Test
    fun `a re-post keeps the sender so the second arrow tap still advances`() {
        // Hop 0: the first post, whose message carries a real sender.
        val firstMessage = mockk<RemoteMessage>(relaxed = true).apply {
            every { data } returns mapOf("_k" to "test_tracking_id")
            every { from } returns SENDER
        }
        KlaviyoNotification(firstMessage).buildNotification(
            mockContext,
            TAG,
            requestCodeBase = REQUEST_CODE_BASE,
            notificationUid = UID
        )

        // The first post stamps the real sender; that intent is the first hop's input.
        active = latestNext() ?: error("the first post built no next-arrow intent")
        val recordedAfterFirstPost = recorded.size
        assertEquals(
            "the first post must stamp the message sender",
            SENDER,
            active?.extras?.get(CarouselConstants.CAROUSEL_SENDER_EXTRA)
        )

        // Hop 1: the receiver advances card 1 -> 2 and re-posts. The message it rebuilds has no
        // `from` (as on device), so only a fix carrying the received sender forward survives.
        KlaviyoCarouselReceiver().onReceive(mockContext, anyCapturedIntent())
        assertEquals("the first arrow tap must advance and re-post", 1, notifies.size)
        assertEquals(1, storedIndex)

        // Capture what the re-post stamped before hop 2 rebuilds the records. Only intents the
        // re-post appended count, so a missing one fails rather than reusing the first post's.
        active = recorded.drop(recordedAfterFirstPost)
            .lastOrNull { it.action == CarouselConstants.CAROUSEL_ACTION_NEXT }
            ?: error("the re-post built no next-arrow intent")
        val repostSender = active?.extras?.get(CarouselConstants.CAROUSEL_SENDER_EXTRA)

        // Hop 2: replay the re-post's own next-arrow intent. On the pre-fix code its sender extra
        // is null, so this bails at the "missing sender" guard and never re-posts — the on-device
        // symptom (advance once, then freeze).
        KlaviyoCarouselReceiver().onReceive(mockContext, anyCapturedIntent())

        assertEquals("the second arrow tap must also re-post", 2, notifies.size)
        assertEquals("the second arrow tap must advance the card", 2, storedIndex)
        assertEquals(
            "a re-post must keep stamping the sender so the next tap is not dropped",
            SENDER,
            repostSender
        )
    }

    // --- Harness -------------------------------------------------------------------------------

    /** The most recently assembled next-arrow intent (the re-post wins over the first post). */
    private fun latestNext(): RecordedIntent? =
        recorded.lastOrNull { it.action == CarouselConstants.CAROUSEL_ACTION_NEXT }

    private var capturedIntent: Intent? = null

    /**
     * Any captured [Intent] to hand the receiver; the constructed proxy is shared, and its getters
     * read from [active], so the identity is irrelevant.
     */
    private fun anyCapturedIntent(): Intent = capturedIntent ?: error("no intent captured")

    private fun stubCounterString() {
        every { mockContext.getString(any(), *anyVararg()) } answers {
            val args = invocation.args[1] as Array<*>
            "${args[0]} / ${args[1]}"
        }
    }

    private fun stubRemoteMessageAccessors() {
        mockkObject(KlaviyoRemoteMessage)
        with(KlaviyoRemoteMessage) {
            every { any<RemoteMessage>().carouselCards } returns cards
            every { any<RemoteMessage>().channel_id } returns "test_channel"
            every { any<RemoteMessage>().title } returns "Test Title"
            every { any<RemoteMessage>().body } returns "Test Body"
            every { any<RemoteMessage>().sound } returns null
            every { any<RemoteMessage>().notificationCount } returns 0
            every { any<RemoteMessage>().notificationPriority } returns
                NotificationCompat.PRIORITY_DEFAULT
            every { any<RemoteMessage>().actionButtons } returns null
            every { any<RemoteMessage>().deepLink } returns null
            every { any<RemoteMessage>().webUrl } returns null
            every { any<RemoteMessage>().getSmallIcon(any()) } returns
                android.R.drawable.ic_dialog_info
            every { any<RemoteMessage>().getColor(any()) } returns null
        }
    }

    private fun stubNotificationBuilder() {
        mockkConstructor(NotificationCompat.Builder::class)
        every {
            anyConstructed<NotificationCompat.Builder>().setContentIntent(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setSmallIcon(any<Int>())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setColor(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setContentTitle(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setContentText(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setStyle(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setSound(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setNumber(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setPriority(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setAutoCancel(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setOnlyAlertOnce(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setCustomContentView(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setCustomBigContentView(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setDeleteIntent(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().addAction(any<NotificationCompat.Action>())
        } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().build() } returns builtNotification
    }

    private fun stubRemoteViews() {
        mockkConstructor(RemoteViews::class)
        every { anyConstructed<RemoteViews>().setTextViewText(any(), any<String>()) } returns Unit
        every { anyConstructed<RemoteViews>().setImageViewBitmap(any(), any()) } returns Unit
        every { anyConstructed<RemoteViews>().setViewVisibility(any(), any()) } returns Unit
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } returns Unit
        every { anyConstructed<RemoteViews>().setImageViewResource(any(), any()) } returns Unit
        every { anyConstructed<RemoteViews>().setContentDescription(any(), any()) } returns Unit
    }

    /**
     * A recording `Intent`. Every construction shares one proxy, so a new [RecordedIntent] begins at
     * each `setClassName` (the builder's first call per broadcast intent); the getters read from
     * [active], which the test points at the hop it wants to replay.
     */
    private fun stubIntents() {
        mockkConstructor(Intent::class)
        every {
            anyConstructed<Intent>().setClassName(any<String>(), any<String>())
        } answers {
            active = RecordedIntent().also { recorded.add(it) }
            self as Intent
        }
        every { anyConstructed<Intent>().setAction(any()) } answers {
            active?.action = firstArg<String>()
            self as Intent
        }
        every { anyConstructed<Intent>().action } answers { active?.action }
        every {
            anyConstructed<Intent>().putExtra(any<String>(), any<String>())
        } answers {
            active?.extras?.put(firstArg(), secondArg<String>())
            self as Intent
        }
        // A real Bundle accepts a null string extra, and the pre-fix builder stamps exactly that
        // (message.from is null after a rebuild). mockk's any<String>() won't match null, so add a
        // null-specific stub; it must come after the general one to take precedence.
        every {
            anyConstructed<Intent>().putExtra(any<String>(), null as String?)
        } answers {
            active?.extras?.put(invocation.args[0] as String, null)
            self as Intent
        }
        every {
            anyConstructed<Intent>().putExtra(any<String>(), any<Int>())
        } answers {
            active?.extras?.put(firstArg(), secondArg<Int>())
            self as Intent
        }
        every {
            anyConstructed<Intent>().putExtra(any<String>(), any<Long>())
        } answers {
            active?.extras?.put(firstArg(), secondArg<Long>())
            self as Intent
        }
        every {
            anyConstructed<Intent>().putExtra(any<String>(), any<Bundle>())
        } answers {
            active?.extras?.put(firstArg(), secondArg<Bundle>())
            self as Intent
        }
        every { anyConstructed<Intent>().getStringExtra(any()) } answers {
            active?.extras?.get(firstArg()) as? String
        }
        every { anyConstructed<Intent>().getIntExtra(any(), any()) } answers {
            (active?.extras?.get(firstArg()) as? Int) ?: secondArg()
        }
        every { anyConstructed<Intent>().getLongExtra(any(), any()) } answers {
            (active?.extras?.get(firstArg()) as? Long) ?: secondArg()
        }
        every { anyConstructed<Intent>().getBundleExtra(any()) } answers {
            active?.extras?.get(firstArg()) as? Bundle
        }
    }

    /**
     * Faithful `RemoteMessage.Builder`: `build()` returns a message whose `from` is null, matching
     * the real implementation, which removes any `"from"` key. That is the whole point — a re-post
     * cannot recover the sender from the rebuilt message.
     */
    private fun stubRemoteMessageBuilder() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            (firstArg<CharSequence?>())?.isEmpty() ?: true
        }
        stubBundle()

        var rebuiltData: Map<String, String> = emptyMap()
        mockkConstructor(RemoteMessage.Builder::class)
        every { anyConstructed<RemoteMessage.Builder>().setData(any()) } answers {
            rebuiltData = invocation.args[0] as Map<String, String>
            self as RemoteMessage.Builder
        }
        // Faithful: the real build() removes any "from" key, so a rebuilt message's from is always
        // null. The payload never carries "from", so this reproduces that exactly.
        every { anyConstructed<RemoteMessage.Builder>().build() } answers {
            mockk<RemoteMessage>(relaxed = true).apply {
                every { data } returns rebuiltData
                every { from } returns rebuiltData["from"]
            }
        }
    }

    private fun stubBundle() {
        val contents = mutableMapOf<String, String>()
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putString(any(), any()) } answers {
            contents[firstArg()] = secondArg()
        }
        every { anyConstructed<Bundle>().getString(any()) } answers { contents[firstArg()] }
        every { anyConstructed<Bundle>().getString(any(), any()) } answers {
            contents[firstArg()] ?: secondArg()
        }
        every { anyConstructed<Bundle>().keySet() } answers { contents.keys.toSet() }
        every { anyConstructed<Bundle>().isEmpty } answers { contents.isEmpty() }
        every { anyConstructed<Bundle>().putAll(any<Bundle>()) } returns Unit
        every { anyConstructed<Bundle>().remove(any()) } returns Unit
    }

    private fun stubPendingIntents() {
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns
            mockk(relaxed = true)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } answers {
            capturedIntent = invocation.args[2] as Intent
            mockk(relaxed = true)
        }
    }
}
