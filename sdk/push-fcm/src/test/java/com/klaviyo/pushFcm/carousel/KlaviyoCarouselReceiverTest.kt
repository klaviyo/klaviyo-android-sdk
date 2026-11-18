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
    }

    private val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
    private val builtNotification = mockk<Notification>(relaxed = true)
    private val compatBuilder = mockk<NotificationCompat.Builder>(relaxed = true)
    private val cards = (1..CARD_COUNT).map {
        CarouselCard(
            position = it,
            imageUrl = mockk(relaxed = true),
            title = "Title $it",
            description = "Description $it",
            action = CarouselCardAction.OpenApp
        )
    }

    /** Index the store returns for the current card; each test sets this. */
    private var storedIndex = 0

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
        every { CarouselStateStore.getIndex(any(), any()) } answers { storedIndex }
        every { CarouselStateStore.setIndex(any(), any(), any()) } answers {
            storedIndex = thirdArg()
        }
        every { CarouselStateStore.clear(any(), any()) } returns Unit

        mockkObject(CarouselImageLoader)
        every { CarouselImageLoader.get(any(), any()) } answers { cachedBitmap }
        every { CarouselImageLoader.loadOne(any(), any()) } returns null
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
        action: String = Constants.CAROUSEL_ACTION_NEXT,
        payload: Bundle? = mockk<Bundle>(relaxed = true)
    ): Intent = mockk<Intent>(relaxed = true).apply {
        every { this@apply.action } returns action
        every { getStringExtra(Constants.NOTIFICATION_TAG_EXTRA) } returns TAG
        every { getStringExtra(Constants.NOTIFICATION_UID_EXTRA) } returns UID
        every { getStringExtra(Constants.CAROUSEL_SENDER_EXTRA) } returns SENDER
        every { getIntExtra(Constants.CAROUSEL_REQUEST_CODE_BASE_EXTRA, any()) } returns
            REQUEST_CODE_BASE
        every { getBundleExtra(Constants.CAROUSEL_PAYLOAD_EXTRA) } returns payload
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

    private fun verifyRepost(index: Int) {
        verify {
            anyConstructed<KlaviyoNotification>().buildNotification(
                mockContext,
                TAG,
                REQUEST_CODE_BASE,
                UID,
                index,
                // The received sender is re-stamped so the next tap can rebuild the message.
                SENDER
            )
        }
        verify { notificationManager.notify(TAG, Constants.NOTIFICATION_ID, builtNotification) }
    }

    @Test
    fun `next advances the index and re-posts under the same tag`() {
        storedIndex = 0

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        verify { CarouselStateStore.setIndex(mockContext, TAG, 1) }
        verifyRepost(index = 1)
    }

    @Test
    fun `next wraps from the last card to the first`() {
        storedIndex = CARD_COUNT - 1

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        verify { CarouselStateStore.setIndex(mockContext, TAG, 0) }
        verifyRepost(index = 0)
    }

    @Test
    fun `prev wraps from the first card to the last`() {
        storedIndex = 0

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_PREV))

        verify { CarouselStateStore.setIndex(mockContext, TAG, CARD_COUNT - 1) }
        verifyRepost(index = CARD_COUNT - 1)
    }

    @Test
    fun `prev decrements a mid-list index`() {
        storedIndex = 2

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_PREV))

        verify { CarouselStateStore.setIndex(mockContext, TAG, 1) }
        verifyRepost(index = 1)
    }

    @Test
    fun `a stored index out of range is clamped before advancing`() {
        storedIndex = 99

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        // clamped to the last card (index 2), then wraps to 0
        verify { CarouselStateStore.setIndex(mockContext, TAG, 0) }
    }

    @Test
    fun `re-post looks up the image by the card's original position`() {
        storedIndex = 0

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        // Cards are 1-based positions 1..3; index 1 is position 2.
        verify { CarouselImageLoader.get(TAG, 2) }
    }

    @Test
    fun `delete clears the index and evicts cached images`() {
        receiverReturning().onReceive(
            mockContext,
            arrowIntent(Constants.CAROUSEL_ACTION_DELETE)
        )

        verify { CarouselStateStore.clear(mockContext, TAG) }
        verify { CarouselImageLoader.evict(TAG) }
        verify(exactly = 0) {
            anyConstructed<KlaviyoNotification>().buildNotification(
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
        val intent = arrowIntent(Constants.CAROUSEL_ACTION_DELETE).apply {
            every { getStringExtra(Constants.NOTIFICATION_TAG_EXTRA) } returns null
        }

        receiverReturning().onReceive(mockContext, intent)

        verify(exactly = 0) { CarouselStateStore.clear(any(), any()) }
        verify(exactly = 0) { CarouselImageLoader.evict(any()) }
    }

    @Test
    fun `a cache miss calls goAsync and finishes the pending result`() {
        cachedBitmap = null
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        val receiver = receiverReturning(pendingResult)

        receiver.onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        verify { receiver["goAsync"]() }
        // The executor thread downloads then reposts; wait for it so the assertions are stable.
        verify(timeout = 5_000) { CarouselImageLoader.loadOne(TAG, cards[1]) }
        verify(timeout = 5_000) { pendingResult.finish() }
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

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        verify { RemoteMessage.Builder(SENDER) }
    }

    @Test
    fun `an arrow intent with no sender still advances and re-posts`() {
        // A re-post's rebuilt message cannot carry `from`, so its own arrow intents may lack the
        // sender extra. That must not be fatal: the rebuild only needs a non-empty `to`, and the
        // tap should still advance the card.
        cachedBitmap = mockk(relaxed = true)
        storedIndex = 0
        val intent = arrowIntent(Constants.CAROUSEL_ACTION_NEXT).apply {
            every { getStringExtra(Constants.CAROUSEL_SENDER_EXTRA) } returns null
        }

        receiverReturning().onReceive(mockContext, intent)

        verify { CarouselStateStore.setIndex(mockContext, TAG, 1) }
        verify { notificationManager.notify(TAG, Constants.NOTIFICATION_ID, builtNotification) }
    }

    @Test
    fun `a cache hit does not call goAsync`() {
        cachedBitmap = mockk(relaxed = true)
        val receiver = receiverReturning(mockk(relaxed = true))

        receiver.onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        verify(exactly = 0) { receiver["goAsync"]() }
        verifyRepost(index = 1)
    }

    @Test
    fun `an unparseable payload is a no-op`() {
        with(KlaviyoRemoteMessage) {
            every { any<RemoteMessage>().carouselCards } returns null
        }

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        verify(exactly = 0) { CarouselStateStore.setIndex(any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `an intent missing its payload is a no-op`() {
        receiverReturning().onReceive(
            mockContext,
            arrowIntent(Constants.CAROUSEL_ACTION_NEXT, payload = null)
        )

        verify(exactly = 0) { CarouselStateStore.setIndex(any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `arrow taps are ignored when notifications are disabled`() {
        every { notificationManager.areNotificationsEnabled() } returns false

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        verify(exactly = 0) { CarouselStateStore.setIndex(any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `an unknown action is ignored`() {
        receiverReturning().onReceive(mockContext, arrowIntent("com.example.unknown"))

        verify(exactly = 0) { CarouselStateStore.setIndex(any(), any(), any()) }
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    @Test
    fun `handleMove failure is contained and does not crash the host app`() {
        // A throw from the store, i.e. something genuinely unexpected mid-handling.
        every { CarouselStateStore.getIndex(any(), any()) } throws IllegalStateException("boom")

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

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

        storedIndex = 0

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_PREV))

        verify { CarouselStateStore.setIndex(mockContext, TAG, CARD_COUNT - 1) }
        verifyRepost(index = CARD_COUNT - 1)
    }

    @Test
    fun `re-post reuses the original uid and request-code base from the intent`() {
        storedIndex = 0

        receiverReturning().onReceive(mockContext, arrowIntent(Constants.CAROUSEL_ACTION_NEXT))

        val tag = slot<String>()
        val uid = slot<String>()
        verify {
            anyConstructed<KlaviyoNotification>().buildNotification(
                mockContext,
                capture(tag),
                REQUEST_CODE_BASE,
                capture(uid),
                any(),
                any()
            )
        }
        assertEquals(TAG, tag.captured)
        assertEquals(UID, uid.captured)
    }
}
