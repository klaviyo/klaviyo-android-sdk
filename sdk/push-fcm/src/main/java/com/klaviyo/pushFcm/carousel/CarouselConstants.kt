package com.klaviyo.pushFcm.carousel

import com.klaviyo.core.Constants

/**
 * Payload keys, intent actions and extras, limits and request-code offsets used only by the
 * carousel notification inside push-fcm. Kept out of core's public [Constants] so they are not part
 * of the SDK's public API; only [Constants.CAROUSEL_CARD_LINK_EXTRA] crosses modules.
 */
internal object CarouselConstants {
    /**
     * Same value as core's private `INTERNAL_PREFIX`: extras with it stay out of analytics event
     * properties.
     */
    private const val INTERNAL_PREFIX = "_klaviyo."

    /**
     * Payload key holding a carousel's cards as a JSON array string. Each card carries an
     * `image_url`, optional `title`/`description`, and an `action` (`open_app`|`deep_link`|
     * `open_url`) with a `url` where required. A carousel notification is only rendered when
     * [MIN_CAROUSEL_CARDS] to [MAX_CAROUSEL_CARDS] valid cards remain after parsing; otherwise the
     * push falls back to the standard notification path.
     */
    const val CAROUSEL_CARDS_PARAMETER = "carousel_cards"

    /**
     * Broadcast action advancing a carousel notification to its previous card. Sent by the
     * notification's prev arrow to `KlaviyoCarouselReceiver`.
     */
    const val CAROUSEL_ACTION_PREV = Constants.PUSH_PREFIX + "carousel.prev"

    /**
     * Broadcast action advancing a carousel notification to its next card. Sent by the
     * notification's next arrow to `KlaviyoCarouselReceiver`.
     */
    const val CAROUSEL_ACTION_NEXT = Constants.PUSH_PREFIX + "carousel.next"

    /**
     * Broadcast action clearing a carousel notification's persisted state. Fired by the
     * notification's `deleteIntent` when the user dismisses it.
     */
    const val CAROUSEL_ACTION_DELETE = Constants.PUSH_PREFIX + "carousel.delete"

    /**
     * Intent extra carrying a carousel push's original `data` map as a [android.os.Bundle], so the
     * carousel receiver can rebuild the [com.google.firebase.messaging.RemoteMessage] and re-post
     * the notification without the process that first displayed it.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [Constants.NOTIFICATION_TAG_EXTRA].
     */
    const val CAROUSEL_PAYLOAD_EXTRA = INTERNAL_PREFIX + "carousel_payload"

    /**
     * Intent extra carrying a carousel push's `from` sender, required to rebuild the
     * [com.google.firebase.messaging.RemoteMessage] in the carousel receiver
     * ([com.google.firebase.messaging.RemoteMessage.Builder] rejects an empty sender).
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [Constants.NOTIFICATION_TAG_EXTRA].
     */
    const val CAROUSEL_SENDER_EXTRA = INTERNAL_PREFIX + "carousel_sender"

    /**
     * Intent extra carrying a carousel notification's original request-code base, so a re-post
     * reuses it and [android.app.PendingIntent.FLAG_UPDATE_CURRENT] replaces the old tokens instead
     * of accumulating new ones.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [Constants.NOTIFICATION_TAG_EXTRA].
     */
    const val CAROUSEL_REQUEST_CODE_BASE_EXTRA = INTERNAL_PREFIX + "carousel_request_code_base"

    /**
     * Intent extra carrying the 1-based original position of the carousel card whose body was
     * tapped. An internal handoff only: because it uses [INTERNAL_PREFIX], `appendKlaviyoExtras`
     * never copies it into the tap's analytics event properties, so it is not yet reported on
     * `$opened_push`. The future card-attribution ticket reads it directly off the intent.
     * Absent on standard push taps.
     */
    const val CAROUSEL_POSITION_EXTRA = INTERNAL_PREFIX + "carousel_position"

    /**
     * Intent extra carrying the carousel's first-post time in epoch milliseconds. A re-post passes
     * it to `setWhen`, because a fresh builder otherwise stamps the current time and Android
     * re-ranks the notification to it, showing "now" and moving it above newer notifications.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [Constants.NOTIFICATION_TAG_EXTRA].
     */
    const val CAROUSEL_POSTED_AT_EXTRA = INTERNAL_PREFIX + "carousel_posted_at"

    /**
     * Minimum number of valid carousel cards required to render a carousel. Below this the push
     * falls back to the standard notification path.
     */
    const val MIN_CAROUSEL_CARDS = 2

    /**
     * Maximum number of carousel cards accepted. Payloads exceeding this fall back to the standard
     * notification path.
     */
    const val MAX_CAROUSEL_CARDS = 5

    /**
     * Request-code offset for a carousel notification's previous-card arrow. Added to the
     * notification's request-code base, like the action-button offsets, so each PendingIntent's
     * request code is unique. Deliberately clear of the body (`base`) and buttons (`base + 1..3`).
     */
    const val CAROUSEL_PREV_OFFSET = 10

    /**
     * Request-code offset for a carousel notification's next-card arrow. See [CAROUSEL_PREV_OFFSET].
     */
    const val CAROUSEL_NEXT_OFFSET = 11

    /**
     * Request-code offset for a carousel notification's delete intent. See [CAROUSEL_PREV_OFFSET].
     */
    const val CAROUSEL_DELETE_OFFSET = 12
}
