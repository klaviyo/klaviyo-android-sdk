package com.klaviyo.core

import android.content.Context

/**
 * Compile-time constants shared across SDK modules
 */
object Constants {
    /**
     * Package prefix used for Klaviyo intent extras and data keys
     */
    const val PACKAGE_PREFIX = "com.klaviyo."

    /**
     * Prefix for push-related manifest `<meta-data>` keys.
     */
    const val PUSH_PREFIX = PACKAGE_PREFIX + "push."

    /**
     * Key-value pairs get special treatment in a few places across multiple packages
     */
    const val KEY_VALUE_PAIRS = "key_value_pairs"

    /**
     * Key carrying an in-app forms draft-preview token: a push key/value pair and a deep link query
     * parameter. Never tracked or logged; see [com.klaviyo.core.forms.FormsPreview].
     */
    const val FORMS_PREVIEW_KEY = "klaviyo-forms-preview"

    /**
     * Klaviyo push messages contain metadata to associate an event with its original transmission
     */
    const val TRACKING_PARAMETER = "_k"

    /**
     * Payload key holding a notification's destination URL. Copied onto every tap intent as
     * [PACKAGE_PREFIX] + this key, so the URL is readable even when no activity declares a matching
     * intent-filter and the intent's `data` is therefore unset.
     */
    const val URL_PARAMETER = "url"

    /**
     * Payload key holding a notification's browser destination URL. Copied onto the tap intent as
     * [PACKAGE_PREFIX] + this key.
     */
    const val WEB_URL_PARAMETER = "web_url"

    /**
     * Payload key holding a notification's action buttons as a JSON array string. Copied onto the
     * tap intent as [PACKAGE_PREFIX] + this key.
     */
    const val ACTION_BUTTONS_PARAMETER = "action_buttons"

    /**
     * Intent extra key holding the destination URL of the action button that was tapped, stamped
     * only on action button intents. Takes precedence over [URL_PARAMETER] when reading a tap's
     * destination: a button carries its own URL, and the body's [URL_PARAMETER] is present on the
     * same intent regardless of which button was tapped.
     */
    const val BUTTON_LINK_PARAMETER = "Button Link"

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
    const val CAROUSEL_ACTION_PREV = PUSH_PREFIX + "carousel.prev"

    /**
     * Broadcast action advancing a carousel notification to its next card. Sent by the
     * notification's next arrow to `KlaviyoCarouselReceiver`.
     */
    const val CAROUSEL_ACTION_NEXT = PUSH_PREFIX + "carousel.next"

    /**
     * Broadcast action clearing a carousel notification's persisted state. Fired by the
     * notification's `deleteIntent` when the user dismisses it.
     */
    const val CAROUSEL_ACTION_DELETE = PUSH_PREFIX + "carousel.delete"

    /**
     * Intent extra key for the notification tag, used to dismiss the notification
     * when an action button is tapped and [handlePush] processes the intent.
     *
     * Uses [INTERNAL_PREFIX] instead of [PACKAGE_PREFIX] to avoid being swept into
     * analytics event properties by [appendKlaviyoExtras].
     */
    private const val INTERNAL_PREFIX = "_klaviyo."
    const val NOTIFICATION_TAG_EXTRA = INTERNAL_PREFIX + "notification_tag"

    /**
     * Intent extra carrying a `web_url` or `open_url` destination for the push trampoline to open in
     * an external handler after `Klaviyo.handlePush` runs.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [NOTIFICATION_TAG_EXTRA].
     */
    const val BROWSER_URL_EXTRA = INTERNAL_PREFIX + "browser_url"

    /**
     * Intent extra carrying an SDK-generated, per-notification unique ID stamped on every Klaviyo
     * notification's tap intents (body and each action button). Used by `Klaviyo.handlePush` as the
     * dedup key when the `_k` tracking payload has no `tm`.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [NOTIFICATION_TAG_EXTRA].
     */
    const val NOTIFICATION_UID_EXTRA = INTERNAL_PREFIX + "notification_uid"

    /**
     * Intent extra marking an intent whose deep link is delivered to the host by another means, so
     * `Klaviyo.handlePush` skips invoking a registered `DeepLinkHandler` for it. The link itself is
     * still delivered — only the handler call is suppressed. Absent → handler is invoked.
     *
     * Set on the trampoline's own intents and removed before the intent is forwarded to the host, so
     * a host that calls `handlePush` itself still reaches its handler.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [NOTIFICATION_TAG_EXTRA].
     */
    const val SUPPRESS_DEEP_LINK_HANDLER_EXTRA = INTERNAL_PREFIX + "suppress_deep_link_handler"

    /**
     * Intent extra carrying a carousel push's original `data` map as a [android.os.Bundle], so the
     * carousel receiver can rebuild the [com.google.firebase.messaging.RemoteMessage] and re-post
     * the notification without the process that first displayed it.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [NOTIFICATION_TAG_EXTRA].
     */
    const val CAROUSEL_PAYLOAD_EXTRA = INTERNAL_PREFIX + "carousel_payload"

    /**
     * Intent extra carrying a carousel push's `from` sender, required to rebuild the
     * [com.google.firebase.messaging.RemoteMessage] in the carousel receiver
     * ([com.google.firebase.messaging.RemoteMessage.Builder] rejects an empty sender).
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [NOTIFICATION_TAG_EXTRA].
     */
    const val CAROUSEL_SENDER_EXTRA = INTERNAL_PREFIX + "carousel_sender"

    /**
     * Intent extra carrying a carousel notification's original request-code base, so a re-post
     * reuses it and [android.app.PendingIntent.FLAG_UPDATE_CURRENT] replaces the old tokens instead
     * of accumulating new ones.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [NOTIFICATION_TAG_EXTRA].
     */
    const val CAROUSEL_REQUEST_CODE_BASE_EXTRA = INTERNAL_PREFIX + "carousel_request_code_base"

    /**
     * Intent extra carrying the 1-based original position of the carousel card whose body was
     * tapped, for `$opened_push` card attribution. Absent on standard push taps.
     * Uses [INTERNAL_PREFIX] to stay out of analytics event properties, like [NOTIFICATION_TAG_EXTRA].
     */
    const val CAROUSEL_POSITION_EXTRA = INTERNAL_PREFIX + "carousel_position"

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

    /**
     * Manifest `<meta-data>` key a host app sets to opt into automatic push open tracking.
     *
     * Lives in core (not push-fcm) because telemetry's push token request must read it, and core
     * cannot depend on push-fcm.
     */
    const val AUTOMATIC_PUSH_OPEN_TRACKING = PUSH_PREFIX + "automatic_push_open_tracking"

    /**
     * Manifest `<meta-data>` key governing the SDK's automatic push token forwarding. Three-valued —
     * an absent key is distinct from an explicit `false`, resolved via
     * [com.klaviyo.core.config.AutomaticPushTokenForwarding]:
     * - **absent**: the SDK forwards only tokens the push provider delivers to
     *   `KlaviyoPushService.onNewToken`.
     * - **`true`**: additionally pulls the current token at `Klaviyo.initialize` and on each foreground,
     *   via `PushTokenFetcher.maybeAutoRegisterPushToken`.
     * - **`false`**: no automatic forwarding by either path.
     *
     * The public `Klaviyo.setPushToken` API is unaffected in all three states, so hosts owning their
     * token pipeline can always forward tokens explicitly.
     *
     * The two call sites read the flag from different sources: the analytics path via `Registry.config`
     * (always post-initialization) and the push-fcm path via the service [android.content.Context]
     * (safe before `Klaviyo.initialize`, which `Registry.config` is not).
     *
     * Lives in core (not push-fcm) for the same reason as [AUTOMATIC_PUSH_OPEN_TRACKING]: telemetry's
     * push token request must read it, and core cannot depend on push-fcm.
     */
    const val AUTOMATIC_PUSH_TOKEN_FORWARDING = PUSH_PREFIX + "automatic_push_token_forwarding"

    /**
     * Fixed notification ID used in all notify/cancel calls.
     * Notifications are uniquely identified by their string tag, not this ID.
     */
    const val NOTIFICATION_ID = 0

    /**
     * URI schemes routed to a web browser. External intents for these schemes add
     * `Intent.CATEGORY_BROWSABLE` so the OS resolves them to a browser rather than
     * a generic activity chooser.
     */
    val WEB_SCHEMES = setOf("http", "https")

    /**
     * URI schemes that compose a message, dispatched externally via `Intent.ACTION_SENDTO`.
     */
    val SENDTO_SCHEMES = setOf("mailto", "sms", "smsto")

    /**
     * URI scheme that opens the dialer, dispatched externally via `Intent.ACTION_DIAL`.
     */
    const val DIAL_SCHEME = "tel"

    /**
     * Full set of URI schemes accepted by the `web_url` field and `open_url` action buttons.
     * Schemes outside this set are silently dropped to prevent routing dangerous or
     * unintended URIs (e.g. intent:, javascript:, file:) through the SDK.
     */
    val ALLOWED_OPEN_URL_SCHEMES = WEB_SCHEMES + SENDTO_SCHEMES + DIAL_SCHEME
}
