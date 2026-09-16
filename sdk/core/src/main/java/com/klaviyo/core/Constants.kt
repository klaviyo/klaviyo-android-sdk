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
     * Intent extra key holding the destination URL of the action button that was tapped, stamped
     * only on action button intents. Takes precedence over [URL_PARAMETER] when reading a tap's
     * destination: a button carries its own URL, and the body's [URL_PARAMETER] is present on the
     * same intent regardless of which button was tapped.
     */
    const val BUTTON_LINK_PARAMETER = "Button Link"

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
