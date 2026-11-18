package com.klaviyo.pushFcm.carousel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Constants.CAROUSEL_ACTION_DELETE
import com.klaviyo.core.Constants.CAROUSEL_ACTION_NEXT
import com.klaviyo.core.Constants.CAROUSEL_ACTION_PREV
import com.klaviyo.core.Constants.CAROUSEL_DELETE_OFFSET
import com.klaviyo.core.Constants.CAROUSEL_NEXT_OFFSET
import com.klaviyo.core.Constants.CAROUSEL_PAYLOAD_EXTRA
import com.klaviyo.core.Constants.CAROUSEL_PREV_OFFSET
import com.klaviyo.core.Constants.CAROUSEL_REQUEST_CODE_BASE_EXTRA
import com.klaviyo.core.Constants.CAROUSEL_SENDER_EXTRA
import com.klaviyo.core.Constants.NOTIFICATION_TAG_EXTRA
import com.klaviyo.core.Constants.NOTIFICATION_UID_EXTRA
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title
import com.klaviyo.push_fcm.R

/**
 * Renders a carousel push onto a [NotificationCompat.Builder].
 *
 * Uses [NotificationCompat.DecoratedCustomViewStyle] with two custom views: a text-only collapsed
 * view and an expanded view carrying the current card's bitmap, title, description, a "n / N"
 * counter and prev/next arrows. Only the expanded view holds the bitmap, so a posted notification
 * carries one card image and stays well under the binder limit.
 *
 * Arrows are [RemoteViews] click targets, never `addAction` — the three-button budget belongs to
 * `action_buttons`, which the caller adds separately. Each arrow broadcasts to
 * [KlaviyoCarouselReceiver], which re-posts the notification in place under the same tag.
 */
internal object CarouselNotificationBuilder {
    /**
     * Apply the carousel style, views and intents to [builder].
     *
     * @param builder The notification builder, already carrying the payload's common settings.
     * @param context The caller's context, used for resources and the arrow broadcasts.
     * @param message The source message, whose `data` and `from` travel on the arrow intents so the
     *  receiver can rebuild the notification without the original process.
     * @param cards The parsed, validated cards (2–5).
     * @param index The 0-based index of the card to display; out-of-range values are clamped.
     * @param tag The notification tag, reused by every arrow broadcast and the re-post.
     * @param notificationUid The notification's per-notification dedup uid, reused on re-post so the
     *  opened-push dedupe key is stable across arrow taps.
     * @param requestCodeBase The notification's request-code base; arrow and delete codes are this
     *  base plus [CAROUSEL_PREV_OFFSET], [CAROUSEL_NEXT_OFFSET] and [CAROUSEL_DELETE_OFFSET].
     * @param autoTracking Whether taps should route through the trampoline.
     * @param contentIntent The body-tap intent for this card, built by the caller through the
     *  standard open-intent path so a card opens its own destination.
     */
    fun apply(
        builder: NotificationCompat.Builder,
        context: Context,
        message: RemoteMessage,
        cards: List<CarouselCard>,
        index: Int,
        tag: String,
        notificationUid: String,
        requestCodeBase: Int,
        autoTracking: Boolean,
        contentIntent: PendingIntent?
    ) {
        val safeIndex = index.coerceIn(0, cards.lastIndex)
        val card = cards[safeIndex]
        val counter = context.getString(
            R.string.klaviyo_carousel_counter,
            safeIndex + 1,
            cards.size
        )

        val collapsed = RemoteViews(context.packageName, R.layout.klaviyo_carousel_collapsed).apply {
            setTextViewText(R.id.klaviyo_carousel_collapsed_title, card.title ?: message.title)
            setTextViewText(R.id.klaviyo_carousel_collapsed_counter, counter)
        }

        val expanded = RemoteViews(context.packageName, R.layout.klaviyo_carousel_expanded).apply {
            val bitmap = CarouselImageLoader.get(tag, card.position)
            if (bitmap != null) {
                setImageViewBitmap(R.id.klaviyo_carousel_image, bitmap)
            } else {
                setViewVisibility(R.id.klaviyo_carousel_image, View.GONE)
            }
            setTextViewText(R.id.klaviyo_carousel_title, card.title ?: message.title)
            setTextViewText(R.id.klaviyo_carousel_description, card.description)
            setTextViewText(R.id.klaviyo_carousel_counter, counter)
            setOnClickPendingIntent(
                R.id.klaviyo_carousel_prev,
                broadcastIntent(
                    context,
                    CAROUSEL_ACTION_PREV,
                    CAROUSEL_PREV_OFFSET,
                    message,
                    tag,
                    notificationUid,
                    requestCodeBase
                )
            )
            setOnClickPendingIntent(
                R.id.klaviyo_carousel_next,
                broadcastIntent(
                    context,
                    CAROUSEL_ACTION_NEXT,
                    CAROUSEL_NEXT_OFFSET,
                    message,
                    tag,
                    notificationUid,
                    requestCodeBase
                )
            )
        }

        builder
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(contentIntent)
            .setDeleteIntent(
                broadcastIntent(
                    context,
                    CAROUSEL_ACTION_DELETE,
                    CAROUSEL_DELETE_OFFSET,
                    message,
                    tag,
                    notificationUid,
                    requestCodeBase
                )
            )

        Registry.log.verbose("Applied carousel view for card ${safeIndex + 1} of ${cards.size}")
    }

    /**
     * Build a broadcast PendingIntent to [KlaviyoCarouselReceiver] carrying everything the receiver
     * needs to re-post (or clear) the notification. No target index travels: the receiver reads the
     * current index from [CarouselStateStore], so a stale intent can never rewind a card.
     *
     * `FLAG_UPDATE_CURRENT` replaces the previous tokens when a re-post reuses the same request
     * codes, rather than piling up new ones.
     */
    private fun broadcastIntent(
        context: Context,
        action: String,
        offset: Int,
        message: RemoteMessage,
        tag: String,
        notificationUid: String,
        requestCodeBase: Int
    ) = PendingIntent.getBroadcast(
        context,
        requestCodeBase + offset,
        // setClassName rather than the 2-arg Intent constructor: the JVM unit-test environment
        // can't satisfy the native ComponentName resolution the constructor performs. Same seam
        // as KlaviyoTrampolineActivity's intent factories.
        Intent().apply {
            setClassName(context.packageName, KlaviyoCarouselReceiver::class.java.name)
            this.action = action
            putExtra(CAROUSEL_PAYLOAD_EXTRA, message.data.toBundle())
            putExtra(CAROUSEL_SENDER_EXTRA, message.from)
            putExtra(NOTIFICATION_TAG_EXTRA, tag)
            putExtra(NOTIFICATION_UID_EXTRA, notificationUid)
            putExtra(CAROUSEL_REQUEST_CODE_BASE_EXTRA, requestCodeBase)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /**
     * Convert the payload's flat string map to a [Bundle] for transport on the arrow intent.
     */
    private fun Map<String, String>.toBundle() = Bundle().apply {
        forEach { (key, value) -> putString(key, value) }
    }
}
