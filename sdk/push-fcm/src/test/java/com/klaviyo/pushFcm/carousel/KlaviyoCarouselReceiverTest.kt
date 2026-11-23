package com.klaviyo.pushFcm.carousel

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Constants
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.KlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCardAction
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.carouselCards
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
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [KlaviyoCarouselReceiver].
 *
 * The receiver rebuilds the notification by constructing a real `RemoteMessage.Builder` and a real
 * `KlaviyoNotification`. Both are constructor-mocked so the receiver's own logic — index
 * arithmetic, tag/UID reuse, cache-miss handling — is what's exercised. `goAsync` is stubbed
 * through a spyk so the cache-miss path can be asserted without waiting on the executor thread.
 */
internal class KlaviyoCarouselReceiverTest : BaseTest() {
    private companion object {
        const val TAG = "carousel-tag"
        const val UID = "test-notification-uid"
        const val SENDER = "test-sender"
        const val REQUEST_CODE_BASE = 1000
        const val CARD_COUNT = 3
        const val POSTED_AT = 1_700_000_000_000L
    }

    private val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
    private val builtNotification = mockk<Notification>(relaxed = true)
    private val compatBuilder = mockk<NotificationCompat.Builder>(relaxed = true)

    // Original positions have a gap (card 2 was invalid and dropped), so a lookup computed from the
    // rendered index instead of the card's own position would miss.
    private val cards = listOf(1, 3, 4).map {
        CarouselCard(
            position = it,
            imageUrl = mockk(relaxed = true),
            title = "Title $it",
            description = "Description $it",
            action = CarouselCardAction.OpenApp
        )
    }

    /**
     * Index the store returns for the current card, or null when the tapped carousel has been
     * replaced; each test sets this.
     */
    private var storedIndex: Int? = 0

    /** Whether the store finds the notification still showing and owned by the tapped UID. */
    private var stillShowing = true

    /** Bitmap returned by the (mocked) cache, or null for a cache miss. */
    private var cachedBitmap: Bitmap? = mockk(relaxed = true)

    @Before
    override fun setup() {
        super.setup()

        // RemoteMessage.Builder's constructor touches Bundle and TextUtils, both JVM stubs.
        stubAndroidForRemoteMessageBuilder()
        mockkConstructor(RemoteMessage.Builder::class)
        every { anyConstructed<RemoteMessage.Builder>().setData(any()) } answers {
            self as RemoteMessage.Builder
        }
        every {
            anyConstructed<RemoteMessage.Builder>().build()
        } returns mockk<RemoteMessage>(relaxed = true)

        // The receiver builds KlaviyoNotification(message).buildNotification(...).
        mockkConstructor(KlaviyoNotification::class)
        every { compatBuilder.build() } returns builtNotification
        every {
            anyConstructed<KlaviyoNotification>().buildNotification(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns compatBuilder

        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notificationManager
        every { notificationManager.areNotificationsEnabled() } returns true

        // The rebuilt message's cards are stubbed, so the payload's real JSON is irrelevant.
        mockkObject(KlaviyoRemoteMessage)
        with(KlaviyoRemoteMessage) {
            every { any<RemoteMessage>().carouselCards } returns cards
        }

        mockkObject(CarouselStateStore)
        every { CarouselStateStore.getIndex(any(), any(), any()) } answers { storedIndex }
        every { CarouselStateStore.advance(any(), any(), any(), any(), any(), any()) } answers {
            if (stillShowing) {
                arg<() -> Unit>(5).invoke()
                storedIndex = arg(4)
            }
            stillShowing
        }
        every { CarouselStateStore.clear(any(), any(), any()) } returns Unit

        mockkObject(CarouselImageLoader)
        every { CarouselImageLoader.get(any(), any()) } answers { cachedBitmap }
        every { CarouselImageLoader.loadAll(any(), any()) } returns emptyMap()
        every { CarouselImageLoader.evict(any()) } returns Unit
    }

    @After
    override fun cleanup() {
        unmockkObject(CarouselImageLoader)
        unmockkObject(CarouselStateStore)
        unmockkObject(KlaviyoRemoteMessage)
        unmockkStatic(NotificationManagerCompat::class)
        unmockkConstructor(KlaviyoNotification::class)
        unmockkConstructor(RemoteMessage.Builder::class)
        unmockkConstructor(Bundle::class)
        unmockkStatic(TextUtils::class)
        super.cleanup()
    }

    /**
     * Stub the JVM Android stubs that `RemoteMessage.Builder`'s constructor reaches. Without these
     * the real constructor throws "not mocked" before any interception can happen.
     */
    private fun stubAndroidForRemoteMessageBuilder() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            (firstArg<CharSequence?>())?.isEmpty() ?: true
        }
        // Model a Bundle as a real string map so the receiver's Bundle -> Map conversion and the
        // Builder's copy both see actual payload contents.
        val bundleContents = mutableMapOf<String, String>()
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putString(any(), any()) } answers {
            bundleContents[firstArg()] = secondArg()
        }
        every { anyConstructed<Bundle>().getString(any()) } answers { bundleContents[firstArg()] }
        every { anyConstructed<Bundle>().getString(any(), any()) } answers {
            bundleContents[firstArg()] ?: secondArg()
        }
        every { anyConstructed<Bundle>().keySet() } answers { bundleContents.keys.toSet() }
        every { anyConstructed<Bundle>().isEmpty } answers { bundleContents.isEmpty() }
        every { anyConstructed<Bundle>().putAll(any<Bundle>()) } returns Unit
        every { anyConstructed<Bundle>().remove(any()) } returns Unit
    }

    /** A spyk receiver whose `goAsync` is stubbed so the cache-miss branch can run without a thread. */
    private fun receiverReturning(pendingResult: BroadcastReceiver.PendingResult? = null):
        KlaviyoCarouselReceiver = spyk(KlaviyoCarouselReceiver()).apply {
        pendingResult?.let { result -> every { this@apply["goAsync"]() } returns result }
    }

    /** Build the arrow intent the notification's RemoteViews would send. */
    private fun arrowIntent(
        action: String = CarouselConstants.CAROUSEL_ACTION_NEXT,
        payload: Bundle? = mockk<Bundle>(relaxed = true),
        postedAt: Long = POSTED_AT
    ): Intent = mockk<Intent>(relaxed = true).apply {
        every { this@apply.action } returns action
        every { getStringExtra(Constants.NOTIFICATION_TAG_EXTRA) } returns TAG
        every { getStringExtra(Constants.NOTIFICATION_UID_EXTRA) } returns UID
        every { getStringExtra(CarouselConstants.CAROUSEL_SENDER_EXTRA) } returns SENDER
        every { getIntExtra(CarouselConstants.CAROUSEL_REQUEST_CODE_BASE_EXTRA, any()) } returns
            REQUEST_CODE_BASE
        every { getBundleExtra(CarouselConstants.CAROUSEL_PAYLOAD_EXTRA) } returns payload
        every { getLongExtra(CarouselConstants.CAROUSEL_POSTED_AT_EXTRA, any()) } returns postedAt
    }

    /** Two valid cards as the wire format sends them, for the payload round-trip test. */
    private fun realCarouselJson(): String = JSONArray(
        listOf(
            mapOf(
                "image_url" to "https://cdn.example.com/one.png",
                "title" to "Card title one",
                "description" to "Description one",
                "action" to "open_app"
            ),
            mapOf(
                "image_url" to "https://cdn.example.com/two.png",
                "title" to "Card title two",
                "description" to "Description two",
                "action" to "open_app"
            )
        )
    ).toString()

    private fun verifyRepost(index: Int, fromIndex: Int) {
        verify {
            anyConstructed<KlaviyoNotification>().buildNotification(
                mockContext,
                TAG,
                REQUEST_CODE_BASE,
                UID,
                index,
                // The received sender is re-stamped so the next tap can rebuild the message.
                SENDER,
                // The first post's time, so the re-post keeps its timestamp and shade position.
                POSTED_AT,
                // An arrow re-post updates the notification without alerting again.
                true
            )
        }
        verify { CarouselStateStore.advance(mockContext, TAG, UID, fromIndex, index, any()) }
        verify { notificationManager.notify(TAG, Constants.NOTIFICATION_ID, builtNotification) }
    }

    @Test
    fun `next advances the index and re-posts under the same tag`() {
        storedIndex = 0

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        verifyRepost(index = 1, fromIndex = 0)
    }

    @Test
    fun `next on the last card does not wrap or re-post`() {
        // Design: the next arrow is hidden on the last card, so a stale tap changes nothing.
        storedIndex = CARD_COUNT - 1

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        verifyNoMove()
    }

    @Test
    fun `prev on the first card does not wrap or re-post`() {
        storedIndex = 0

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_PREV)
        )

        verifyNoMove()
    }

    private fun verifyNoMove() {
        verify(exactly = 0) { CarouselStateStore.advance(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `prev decrements a mid-list index`() {
        storedIndex = 2

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_PREV)
        )

        verifyRepost(index = 1, fromIndex = 2)
    }

    @Test
    fun `a stored index out of range is clamped before moving`() {
        storedIndex = 99

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_PREV)
        )

        // clamped to the last card (index 2), then back one
        verifyRepost(index = 1, fromIndex = 99)
    }

    @Test
    fun `re-post looks up the image by the card's original position`() {
        storedIndex = 0

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        // Index 1 is the card at original position 3, cached under the notification UID.
        verify { CarouselImageLoader.get(UID, 3) }
        verify(exactly = 0) { CarouselImageLoader.get(any(), 2) }
    }

    @Test
    fun `delete clears the dismissed generation's index and images`() {
        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_DELETE)
        )

        verify { CarouselStateStore.clear(mockContext, TAG, UID) }
        verify { CarouselImageLoader.evict(UID) }
        verify(exactly = 0) {
            anyConstructed<KlaviyoNotification>().buildNotification(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `delete ignores an intent with no tag`() {
        val intent = arrowIntent(CarouselConstants.CAROUSEL_ACTION_DELETE).apply {
            every { getStringExtra(Constants.NOTIFICATION_TAG_EXTRA) } returns null
        }

        receiverReturning().onReceive(mockContext, intent)

        verify(exactly = 0) { CarouselStateStore.clear(any(), any(), any()) }
        verify(exactly = 0) { CarouselImageLoader.evict(any()) }
    }

    @Test
    fun `delete ignores an intent with no uid`() {
        val intent = arrowIntent(CarouselConstants.CAROUSEL_ACTION_DELETE).apply {
            every { getStringExtra(Constants.NOTIFICATION_UID_EXTRA) } returns null
        }

        receiverReturning().onReceive(mockContext, intent)

        verify(exactly = 0) { CarouselStateStore.clear(any(), any(), any()) }
        verify(exactly = 0) { CarouselImageLoader.evict(any()) }
    }

    @Test
    fun `a tap on a replaced carousel is ignored`() {
        // A re-send reused the tag; this intent's UID no longer owns it.
        storedIndex = null

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        verifyNoMove()
    }

    @Test
    fun `an arrow intent without a post time re-posts with the current time`() {
        // An arrow intent built before the post-time extra existed reads the 0L default.
        storedIndex = 0

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT, postedAt = 0L)
        )

        verify {
            anyConstructed<KlaviyoNotification>().buildNotification(
                mockContext,
                TAG,
                REQUEST_CODE_BASE,
                UID,
                1,
                SENDER,
                null,
                true
            )
        }
    }

    @Test
    fun `a re-post the store refuses does not notify`() {
        // Replaced or dismissed while the image downloaded: advance declines to run the post.
        stillShowing = false

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        verify { CarouselStateStore.advance(mockContext, TAG, UID, 0, 1, any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
        // Building would rewrite the shown card's PendingIntent extras, so it must not happen.
        verify(exactly = 0) {
            anyConstructed<KlaviyoNotification>().buildNotification(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        assertEquals(0, storedIndex)
    }

    @Test
    fun `a cache miss calls goAsync and finishes the pending result`() {
        cachedBitmap = null
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        val receiver = receiverReturning(pendingResult)

        receiver.onReceive(mockContext, arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT))

        verify { receiver["goAsync"]() }
        // The executor thread downloads then reposts; wait for it so the assertions are stable.
        // Card 1 is fetched alongside for the collapsed thumbnail; loadAll skips it if cached.
        verify(timeout = 5_000) { CarouselImageLoader.loadAll(UID, listOf(cards[1], cards[0])) }
        verify(timeout = 5_000) { pendingResult.finish() }
        // No bitmap came back, but the card still advances.
        verifyRepost(index = 1, fromIndex = 0)
    }

    @Test
    fun `a cache miss on a move back to card 1 loads it once under the deadline`() {
        storedIndex = 1
        cachedBitmap = null
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)

        receiverReturning(pendingResult)
            .onReceive(mockContext, arrowIntent(CarouselConstants.CAROUSEL_ACTION_PREV))

        verify(timeout = 5_000) { CarouselImageLoader.loadAll(UID, listOf(cards[0])) }
        verify(timeout = 5_000) { pendingResult.finish() }
        verifyRepost(index = 0, fromIndex = 1)
    }

    @Test
    fun `the receiver converts the payload bundle to a string map`() {
        // The receiver rebuilds the RemoteMessage from CAROUSEL_PAYLOAD_EXTRA via its private
        // Bundle.toStringMap(). A bug there (dropped keys, non-string values leaking through)
        // would silently stop the carousel from ever re-posting, so exercise it directly.
        val payload = mockk<Bundle>(relaxed = true)
        every { payload.keySet() } returns setOf("_k", "carousel_cards", "not_a_string")
        every { payload.getString("_k") } returns "test_tracking_id"
        every { payload.getString("carousel_cards") } returns realCarouselJson()
        // A non-string extra must be dropped rather than crash the map build.
        every { payload.getString("not_a_string") } returns null

        val method = KlaviyoCarouselReceiver::class.java.declaredMethods
            .first { it.name == "toStringMap" }
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val converted = method.invoke(KlaviyoCarouselReceiver(), payload) as Map<String, String>

        assertEquals("test_tracking_id", converted["_k"])
        assertEquals(realCarouselJson(), converted["carousel_cards"])
        assertEquals(
            "non-string extras must be dropped, not passed through",
            setOf("_k", "carousel_cards"),
            converted.keys
        )
    }

    @Test
    fun `the rebuilt message is constructed with the intent's sender`() {
        // RemoteMessage.Builder rejects an empty sender, so a dropped CAROUSEL_SENDER_EXTRA would
        // throw on every arrow tap and leave the carousel frozen on one card.
        cachedBitmap = mockk(relaxed = true)

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        verify { RemoteMessage.Builder(SENDER) }
    }

    @Test
    fun `an arrow intent with no sender still advances and re-posts`() {
        // A re-post's rebuilt message cannot carry `from`, so its own arrow intents may lack the
        // sender extra. That must not be fatal: the rebuild only needs a non-empty `to`, and the
        // tap should still advance the card.
        cachedBitmap = mockk(relaxed = true)
        storedIndex = 0
        val intent = arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT).apply {
            every { getStringExtra(CarouselConstants.CAROUSEL_SENDER_EXTRA) } returns null
        }

        receiverReturning().onReceive(mockContext, intent)

        verify {
            notificationManager.notify(
                TAG,
                Constants.NOTIFICATION_ID,
                builtNotification
            )
        }
    }

    @Test
    fun `a cache hit does not call goAsync`() {
        cachedBitmap = mockk(relaxed = true)
        val receiver = receiverReturning(mockk(relaxed = true))

        receiver.onReceive(mockContext, arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT))

        verify(exactly = 0) { receiver["goAsync"]() }
        verifyRepost(index = 1, fromIndex = 0)
    }

    @Test
    fun `an unparseable payload is a no-op`() {
        with(KlaviyoRemoteMessage) {
            every { any<RemoteMessage>().carouselCards } returns null
        }

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        verify(exactly = 0) { CarouselStateStore.advance(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `an intent missing its payload is a no-op`() {
        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT, payload = null)
        )

        verify(exactly = 0) { CarouselStateStore.advance(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `arrow taps are ignored when notifications are disabled`() {
        every { notificationManager.areNotificationsEnabled() } returns false

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        verify(exactly = 0) { CarouselStateStore.advance(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `an unknown action is ignored`() {
        receiverReturning().onReceive(mockContext, arrowIntent("com.example.unknown"))

        verify(exactly = 0) { CarouselStateStore.advance(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `handleMove failure is contained and does not crash the host app`() {
        // A throw from the store, i.e. something genuinely unexpected mid-handling.
        every { CarouselStateStore.getIndex(any(), any(), any()) } throws IllegalStateException(
            "boom"
        )

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        verify { spyLog.error(any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `pre-init a PREV tap is handled when the registry has no config`() {
        // Simulate a tap before Klaviyo.initialize: every Registry service is gone, so any
        // accidental Registry.config or Registry.dataStore access throws MissingConfig.
        unmockkObject(Registry)
        mockkObject(Registry)
        every { Registry.log } returns spyLog

        storedIndex = 2

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_PREV)
        )

        verifyRepost(index = 1, fromIndex = 2)
    }

    @Test
    fun `re-post reuses the original uid and request-code base from the intent`() {
        storedIndex = 0

        receiverReturning().onReceive(
            mockContext,
            arrowIntent(CarouselConstants.CAROUSEL_ACTION_NEXT)
        )

        val tag = slot<String>()
        val uid = slot<String>()
        verify {
            anyConstructed<KlaviyoNotification>().buildNotification(
                mockContext,
                capture(tag),
                REQUEST_CODE_BASE,
                capture(uid),
                any(),
                any(),
                any(),
                any()
            )
        }
        assertEquals(TAG, tag.captured)
        assertEquals(UID, uid.captured)
    }
}
