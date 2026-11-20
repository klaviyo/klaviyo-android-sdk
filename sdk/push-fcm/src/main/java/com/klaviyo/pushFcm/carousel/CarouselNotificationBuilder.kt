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
import com.klaviyo.core.Constants.MAX_CAROUSEL_CARDS
import com.klaviyo.core.Constants.NOTIFICATION_TAG_EXTRA
import com.klaviyo.core.Constants.NOTIFICATION_UID_EXTRA
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title
import com.klaviyo.push_fcm.R

/**
 * Renders a carousel push onto a [NotificationCompat.Builder].
 *
 * Uses [NotificationCompat.DecoratedCustomViewStyle] with two custom views. The collapsed view
 * shows the notification title and body with a thumbnail of card 1. The expanded view shows the
 * current card only: its image, with prev/next arrows and page dots over it, then the card title
 * and description. The arrows are hidden on the first and last card, so the carousel does not wrap.
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
     * @param message The source message, whose `data` travels on the arrow intents so the receiver
     *  can rebuild the notification without the original process.
     * @param cards The parsed, validated cards (2–5).
     * @param index The 0-based index of the card to display; out-of-range values are clamped.
     * @param tag The notification tag, reused by every arrow broadcast and the re-post.
     * @param notificationUid The notification's per-notification dedup uid, reused on re-post so the
     *  opened-push dedupe key is stable across arrow taps. Also the key its card images are cached
     *  under in [CarouselImageLoader].
     * @param requestCodeBase The notification's request-code base; arrow and delete codes are this
     *  base plus [CAROUSEL_PREV_OFFSET], [CAROUSEL_NEXT_OFFSET] and [CAROUSEL_DELETE_OFFSET].
     * @param sender The FCM sender stamped on every arrow intent so the receiver can rebuild the
     *  message. Supplied by the caller (not read from [message]) because a re-post's rebuilt
     *  message has no `from`; see [KlaviyoNotification.buildNotification].
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
        sender: String?,
        contentIntent: PendingIntent?
    ) {
        val safeIndex = index.coerceIn(0, cards.lastIndex)
        val card = cards[safeIndex]

        val collapsed = RemoteViews(context.packageName, R.layout.klaviyo_carousel_collapsed).apply {
            setTextViewText(R.id.klaviyo_carousel_collapsed_title, message.title)
            setTextViewText(R.id.klaviyo_carousel_collapsed_body, message.body)
            // Always card 1, whichever card is on screen, so the collapsed view never changes.
            val thumbnail = CarouselImageLoader.thumbnail(notificationUid, cards.first().position)
            if (thumbnail != null) {
                setImageViewBitmap(R.id.klaviyo_carousel_thumbnail, thumbnail)
            } else {
                setViewVisibility(R.id.klaviyo_carousel_thumbnail, View.GONE)
            }
        }

        val expanded = RemoteViews(context.packageName, R.layout.klaviyo_carousel_expanded).apply {
            val bitmap = CarouselImageLoader.get(notificationUid, card.position)
            if (bitmap != null) {
                setImageViewBitmap(R.id.klaviyo_carousel_image, bitmap)
            } else {
                // The frame's placeholder stays, so the arrows and dots remain usable.
                setViewVisibility(R.id.klaviyo_carousel_image, View.GONE)
            }
            setOptionalText(R.id.klaviyo_carousel_title, card.title)
            setOptionalText(R.id.klaviyo_carousel_description, card.description)
            setDots(context, safeIndex, cards.size)

            if (safeIndex > 0) {
                setOnClickPendingIntent(
                    R.id.klaviyo_carousel_prev,
                    broadcastIntent(
                        context,
                        CAROUSEL_ACTION_PREV,
                        CAROUSEL_PREV_OFFSET,
                        message,
                        tag,
                        notificationUid,
                        requestCodeBase,
                        sender
                    )
                )
            } else {
                setViewVisibility(R.id.klaviyo_carousel_prev, View.GONE)
            }

            if (safeIndex < cards.lastIndex) {
                setOnClickPendingIntent(
                    R.id.klaviyo_carousel_next,
                    broadcastIntent(
                        context,
                        CAROUSEL_ACTION_NEXT,
                        CAROUSEL_NEXT_OFFSET,
                        message,
                        tag,
                        notificationUid,
                        requestCodeBase,
                        sender
                    )
                )
            } else {
                setViewVisibility(R.id.klaviyo_carousel_next, View.GONE)
            }
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
                    requestCodeBase,
                    sender
                )
            )

        Registry.log.verbose("Applied carousel view for card ${safeIndex + 1} of ${cards.size}")
    }

    /**
     * Page-dot views in the expanded layout, one per possible card ([MAX_CAROUSEL_CARDS]).
     */
    private val DOT_IDS = listOf(
        R.id.klaviyo_carousel_dot_1,
        R.id.klaviyo_carousel_dot_2,
        R.id.klaviyo_carousel_dot_3,
        R.id.klaviyo_carousel_dot_4,
        R.id.klaviyo_carousel_dot_5
    )

    /**
     * Show one dot per card, fill the dot for [index], hide the unused dots, and describe the
     * position for accessibility ("Card 2 of 4").
     */
    private fun RemoteViews.setDots(context: Context, index: Int, count: Int) {
        DOT_IDS.forEachIndexed { dot, id ->
            when {
                dot >= count -> setViewVisibility(id, View.GONE)
                dot == index -> setImageViewResource(id, R.drawable.klaviyo_carousel_dot_active)
                else -> setImageViewResource(id, R.drawable.klaviyo_carousel_dot_inactive)
            }
        }
        setContentDescription(
            R.id.klaviyo_carousel_dots,
            context.getString(R.string.klaviyo_carousel_position_description, index + 1, count)
        )
    }

    /**
     * Set [text] on [viewId], or hide the view when the card has no such field.
     */
    private fun RemoteViews.setOptionalText(viewId: Int, text: String?) {
        if (text.isNullOrBlank()) {
            setViewVisibility(viewId, View.GONE)
        } else {
            setTextViewText(viewId, text)
        }
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
        requestCodeBase: Int,
        sender: String?
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
            putExtra(CAROUSEL_SENDER_EXTRA, sender)
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
