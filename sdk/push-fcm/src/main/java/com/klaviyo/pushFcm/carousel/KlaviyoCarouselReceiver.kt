package com.klaviyo.pushFcm.carousel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Constants
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.carouselCards
import java.util.concurrent.Executors

/**
 * Handles carousel notification arrow taps and dismissal.
 *
 * Registered in the push-fcm manifest as a non-exported receiver with no intent filter: it is only
 * ever reached by explicit broadcast from a carousel notification's RemoteViews arrows and delete
 * intent.
 *
 * PREV/NEXT: rebuild the [RemoteMessage] from the payload and sender carried on the intent, read
 * the current card index from [CarouselStateStore], move it one card (stopping at the first and last
 * card, where the matching arrow is hidden), then re-post the
 * notification under the same tag, with the original `NOTIFICATION_UID_EXTRA` and request-code base
 * so the opened-push dedupe key is stable and `FLAG_UPDATE_CURRENT` replaces the old tokens rather
 * than piling up new ones. The image cache is keyed by the card's original position, not the store
 * index. On a cache miss (typically after process death) it downloads only the target card on a
 * background thread via [goAsync] before re-posting.
 *
 * DELETE: clear the persisted index and evict the cached images.
 *
 * Pre-init safety: this receiver never touches `Registry.config` or `Registry.dataStore` — both
 * throw before `Klaviyo.initialize`, and an arrow tap can arrive before the host has initialized.
 * It uses only the passed-in [Context] and [Registry.log].
 */
internal class KlaviyoCarouselReceiver : BroadcastReceiver() {
    companion object {
        /**
         * Single background thread for the cache-miss download. `goAsync` gives the receiver a few
         * seconds off the main thread, which is plenty for one image.
         */
        private val executor = Executors.newSingleThreadExecutor()

        /**
         * Non-empty stand-in passed to [RemoteMessage.Builder] when the arrow intent has no sender.
         * The builder only validates that its `to` argument is non-empty and never uses it to
         * populate `from`, so this only satisfies the constructor. The real sender, when present,
         * is re-stamped onto the intents this receiver builds.
         */
        private const val FALLBACK_SENDER = "com.klaviyo.carousel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                Constants.CAROUSEL_ACTION_PREV -> handleMove(context, intent, direction = -1)
                Constants.CAROUSEL_ACTION_NEXT -> handleMove(context, intent, direction = 1)
                Constants.CAROUSEL_ACTION_DELETE -> handleDelete(context, intent)
                else -> Registry.log.warning("KlaviyoCarouselReceiver ignoring unknown action")
            }
        } catch (e: Exception) {
            // A tap on a notification must never crash the host app.
            Registry.log.error("KlaviyoCarouselReceiver failed to handle action", e)
        }
    }

    /**
     * Advance the carousel one card and re-post it.
     *
     * @param direction -1 for previous, +1 for next.
     */
    private fun handleMove(context: Context, intent: Intent, direction: Int) {
        val tag = intent.getStringExtra(Constants.NOTIFICATION_TAG_EXTRA)
            ?: return logMissing("notification tag")
        val notificationUid = intent.getStringExtra(Constants.NOTIFICATION_UID_EXTRA)
            ?: return logMissing("notification uid")
        val requestCodeBase = intent.getIntExtra(
            Constants.CAROUSEL_REQUEST_CODE_BASE_EXTRA,
            Int.MIN_VALUE
        ).takeIf { it != Int.MIN_VALUE } ?: return logMissing("request code base")
        // A rebuilt RemoteMessage cannot carry `from` (Builder(String) sets the `to` field), so a
        // re-post's arrow intents may lack the sender. Treat it as optional and fall back to a
        // fixed non-empty placeholder for the rebuild, which only needs a non-empty `to`.
        val sender = intent.getStringExtra(Constants.CAROUSEL_SENDER_EXTRA)
        val payload = intent.getBundleExtra(Constants.CAROUSEL_PAYLOAD_EXTRA)
            ?: return logMissing("payload")

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Registry.log.warning("Carousel arrow tapped with notifications disabled; ignoring")
            return
        }

        val message = RemoteMessage.Builder(sender ?: FALLBACK_SENDER)
            .setData(payload.toStringMap())
            .build()
        val cards = message.carouselCards

        if (cards.isNullOrEmpty()) {
            Registry.log.warning("Carousel payload no longer parses; ignoring arrow tap")
            return
        }

        val currentIndex = CarouselStateStore.getIndex(context, tag).coerceIn(0, cards.lastIndex)
        // No wrap-around: the arrow is hidden at each end, so a tap past the end can only come
        // from a stale view (e.g. a double tap before the re-post lands) and changes nothing.
        val nextIndex = (currentIndex + direction).coerceIn(0, cards.lastIndex)
        if (nextIndex == currentIndex) {
            Registry.log.verbose("Carousel arrow tapped at the end of the cards; ignoring")
            return
        }
        CarouselStateStore.setIndex(context, tag, nextIndex)

        val card = cards[nextIndex]
        val notification = KlaviyoNotification(message)

        if (CarouselImageLoader.get(tag, card.position) != null) {
            repost(
                context,
                notification,
                card,
                tag,
                notificationUid,
                requestCodeBase,
                nextIndex,
                sender
            )
            return
        }

        // Cache miss (usually process death): download just this card off the main thread, then
        // re-post whether or not it arrives. finish() must run on every path.
        val pendingResult = goAsync()
        try {
            executor.execute {
                try {
                    // Card 1 also feeds the collapsed thumbnail, so fetch both in one parallel,
                    // deadline-bounded pass when card 1's image is gone too.
                    val first = cards.first()
                    if (first.position != card.position &&
                        CarouselImageLoader.get(tag, first.position) == null
                    ) {
                        CarouselImageLoader.loadAll(tag, listOf(card, first))
                    } else {
                        CarouselImageLoader.loadOne(tag, card)
                    }
                    repost(
                        context,
                        notification,
                        card,
                        tag,
                        notificationUid,
                        requestCodeBase,
                        nextIndex,
                        sender
                    )
                } catch (e: Exception) {
                    Registry.log.error("Carousel cache-miss re-post failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (e: Exception) {
            // Executor rejected the task: re-post without the image rather than leaving the card
            // unchanged behind a fired tap.
            Registry.log.error("Carousel cache-miss download could not be scheduled", e)
            try {
                repost(
                    context,
                    notification,
                    card,
                    tag,
                    notificationUid,
                    requestCodeBase,
                    nextIndex,
                    sender
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Re-post the carousel notification in place under the original tag, UID and request codes.
     */
    @WorkerThread
    @Suppress("MissingPermission") // Gated by areNotificationsEnabled() in handleMove().
    private fun repost(
        context: Context,
        notification: KlaviyoNotification,
        card: CarouselCard,
        tag: String,
        notificationUid: String,
        requestCodeBase: Int,
        index: Int,
        sender: String?
    ) {
        val built = notification.buildNotification(
            context,
            tag,
            requestCodeBase = requestCodeBase,
            notificationUid = notificationUid,
            carouselIndex = index,
            carouselSender = sender
        )
        NotificationManagerCompat
            .from(context)
            .notify(tag, Constants.NOTIFICATION_ID, built.build())
        Registry.log.verbose("Re-posted carousel card ${card.position} under tag '$tag'")
    }

    /**
     * Dismissal: forget the index and drop the cached images.
     */
    private fun handleDelete(context: Context, intent: Intent) {
        val tag = intent.getStringExtra(Constants.NOTIFICATION_TAG_EXTRA)
            ?: return logMissing("notification tag")
        CarouselStateStore.clear(context, tag)
        CarouselImageLoader.evict(tag)
    }

    private fun logMissing(field: String): Unit =
        Registry.log.warning("KlaviyoCarouselReceiver intent missing $field; ignoring")

    /**
     * Convert the payload [Bundle] back to the flat string map [RemoteMessage] expects.
     */
    private fun Bundle.toStringMap(): Map<String, String> = keySet()
        .mapNotNull { key -> getString(key)?.let { key to it } }
        .toMap()
}
