package com.klaviyo.analytics

import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.klaviyo.analytics.linking.DeepLinkHandler
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Constants
import com.klaviyo.core.Constants.ACTION_BUTTONS_PARAMETER
import com.klaviyo.core.Constants.BUTTON_LINK_PARAMETER
import com.klaviyo.core.Constants.KEY_VALUE_PAIRS
import com.klaviyo.core.Constants.PACKAGE_PREFIX
import com.klaviyo.core.Constants.TRACKING_PARAMETER
import com.klaviyo.core.Constants.URL_PARAMETER
import com.klaviyo.core.Constants.WEB_URL_PARAMETER
import com.klaviyo.core.Operation
import com.klaviyo.core.Registry
import com.klaviyo.core.forms.FormsPreview
import com.klaviyo.core.forms.FormsPreviewTokenSink
import com.klaviyo.core.safeApply
import com.klaviyo.core.safeCall
import com.klaviyo.core.utils.BoundedIdSet
import com.klaviyo.core.utils.JSONUtil.toHashMap
import java.io.Serializable
import java.util.Queue
import org.json.JSONObject

internal object KlaviyoPushOpenHandler {

    /**
     * Push delivery IDs already handled within this process, so a single tap records one
     * `$opened_push`. See [handle] for how entries are matched and added.
     */
    private val handledPushDeliveries = BoundedIdSet()

    /**
     * Push delivery IDs whose deep link has already been dispatched to a registered handler.
     * Tracked separately from [handledPushDeliveries] because a caller may suppress dispatch while
     * still recording the open, so the two stages do not advance together.
     */
    private val dispatchedDeepLinks = BoundedIdSet()

    /**
     * Key within the `_k` tracking payload whose value uniquely identifies a single push delivery.
     */
    private const val PUSH_DELIVERY_KEY = "tm"

    /**
     * Core push-open handling: guards, event enqueue, notification dismissal, deep-link dispatch.
     * Called by [Klaviyo.handlePush]; not meant for direct use outside this module.
     *
     * Tracking/dismissal and deep-link dispatch each happen at most once per delivery, counted
     * separately: an intent flagged with [Constants.SUPPRESS_DEEP_LINK_HANDLER_EXTRA] records the
     * open without consuming the dispatch, so the unflagged copy forwarded to the host still
     * reaches a registered [DeepLinkHandler].
     */
    internal fun handle(intent: Intent?, preInitQueue: Queue<Operation<Unit>>) {
        if (intent == null || !Klaviyo.isKlaviyoNotificationIntent(intent)) {
            Registry.log.verbose("Non-Klaviyo intent ignored")
            return
        }

        // Track each push delivery at most once per process. The trampoline calls handlePush and
        // forwards the same intent to the host, so a manual handlePush call (or singleTask
        // re-entry) would otherwise double-track one tap. A delivery with no id is never deduped.
        val deliveryId = intent.pushDeliveryId
        val isNewDelivery = deliveryId == null || handledPushDeliveries.markOnce(deliveryId)

        val previewToken = stripFormsPreviewToken(intent)
        if (isNewDelivery) previewToken?.let(::startFormsPreview)

        if (isNewDelivery) {
            // Create and enqueue an $opened_push. safeApply(preInitQueue) buffers this for replay
            // if handlePush runs before initialize(), and guards against unexpected failures.
            safeApply(preInitQueue) {
                val state = Registry.get<State>()
                val event = Event(EventMetric.OPENED_PUSH)
                event.appendKlaviyoExtras(intent)
                state.pushToken?.let { event[EventKey.PUSH_TOKEN] = it }
                // Not using Klaviyo.createEvent here to avoid nesting safeApply calls
                state.createEvent(event, state.getAsProfile())
            }

            // Dismiss the notification if opened via an action button. Body taps auto-cancel via
            // setAutoCancel(true) on the builder; action button taps don't (standard Android
            // behavior).
            safeApply {
                val notificationTag = intent.getStringExtra(Constants.NOTIFICATION_TAG_EXTRA)
                if (notificationTag != null) {
                    NotificationManagerCompat
                        .from(Registry.config.applicationContext)
                        .cancel(notificationTag, Constants.NOTIFICATION_ID)
                }
            }
        } else {
            Registry.log.verbose("Ignoring duplicate push open")
        }

        // Returns before marking the delivery below, so the unflagged intent forwarded to the host
        // still has its dispatch available.
        if (intent.getBooleanExtra(Constants.SUPPRESS_DEEP_LINK_HANDLER_EXTRA, false)) {
            Registry.log.verbose("Deep link delivered by intent; not invoking handler")
            return
        }

        // If the notification carries a deep link and a handler is registered, invoke it. Otherwise
        // do nothing — the host already received the appropriate intent.
        safeApply {
            val deepLink = intent.data
            if (deepLink != null && DeepLinking.isHandlerRegistered) {
                // Dispatch is tracked separately from the open above: an entry point may suppress
                // dispatch while still tracking, so one delivery can reach this point twice.
                if (deliveryId == null || dispatchedDeepLinks.markOnce(deliveryId)) {
                    DeepLinking.handleDeepLink(deepLink)
                } else {
                    Registry.log.verbose("Ignoring duplicate deep link dispatch")
                }
            }
        }
    }

    /**
     * Removes an in-app forms preview token from [intent] and returns it, or null if it carries
     * none. Applied to every intent copy of a delivery.
     *
     * A `key_value_pairs` extra that is not a JSON object is removed from [intent]. Otherwise the
     * token is the string value in the key/value pairs, else the first of these links that carries
     * it: the intent data, then the payload `Button Link`, `url` and `web_url` extras, then the
     * trampoline's browser URL extra. The token is removed from the key/value pairs and from every
     * one of those links, so it reaches neither `$opened_push`, nor the host's deep link handling,
     * nor the URL the trampoline opens in the browser. The preview parameter is also removed from
     * each button `url` in the `action_buttons` extra, whether or not a token was found elsewhere.
     * The returned token is not validated.
     */
    private fun stripFormsPreviewToken(intent: Intent): String? = safeCall {
        val keyValuePairsExtra = PACKAGE_PREFIX + KEY_VALUE_PAIRS
        val linkExtras = listOf(
            PACKAGE_PREFIX + BUTTON_LINK_PARAMETER,
            PACKAGE_PREFIX + URL_PARAMETER,
            PACKAGE_PREFIX + WEB_URL_PARAMETER,
            Constants.BROWSER_URL_EXTRA
        )

        intent.getStringExtra(keyValuePairsExtra)?.let {
            if (runCatching { JSONObject(it) }.isFailure) intent.removeExtra(keyValuePairsExtra)
        }
        val actionButtonsExtra = PACKAGE_PREFIX + ACTION_BUTTONS_PARAMETER
        intent.getStringExtra(actionButtonsExtra)?.let { buttons ->
            val stripped = FormsPreview.actionButtonsWithoutPreviewToken(buttons)
            if (stripped != buttons) intent.putExtra(actionButtonsExtra, stripped)
        }

        val links = listOfNotNull(intent.data?.toString()) +
            linkExtras.mapNotNull { intent.getStringExtra(it) }

        val token = FormsPreview.previewTokenFromKeyValuePairs(
            intent.getStringExtra(keyValuePairsExtra)
        ) ?: links.firstNotNullOfOrNull { FormsPreview.previewTokenParameter(it) }
            ?: return@safeCall null

        intent.getStringExtra(keyValuePairsExtra)?.let {
            intent.putExtra(keyValuePairsExtra, FormsPreview.keyValuePairsWithoutPreviewToken(it))
        }
        intent.data?.let { link ->
            val stripped = FormsPreview.withoutPreviewTokenParameter(link.toString())
            if (stripped != link.toString()) intent.data = stripped.toUri()
        }
        linkExtras.forEach { extra ->
            intent.getStringExtra(extra)?.let { link ->
                val stripped = FormsPreview.withoutPreviewTokenParameter(link)
                if (stripped != link) intent.putExtra(extra, stripped)
            }
        }

        token
    }

    /**
     * Hands [token] to the forms module if it is well-formed, else logs a warning that omits it.
     * Forms not being installed is not an error.
     */
    private fun startFormsPreview(token: String) = safeApply {
        if (!FormsPreview.isValidToken(token)) {
            Registry.log.warning("Ignoring opened push with a malformed forms preview token")
            return@safeApply
        }
        Registry.getOrNull<FormsPreviewTokenSink>()?.startPreview(token)
            ?: Registry.log.debug(
                "Opened push carries a forms preview token, but forms is not installed"
            )
    }

    /**
     * Dedup key for this intent's push delivery, or `null` if none is available. Prefers the `tm`
     * field of the `_k` payload (a per-delivery ULID on campaign sends), else the SDK-generated
     * [Constants.NOTIFICATION_UID_EXTRA] stamped on trampoline intents. Both are copied forward to
     * the host's intent, so the trampoline call and a leftover manual call for the same tap resolve
     * to the same key while distinct notifications stay distinct.
     *
     * Deliberately not the raw `_k`: minus `tm` it is per-message metadata shared across deliveries,
     * so it would collapse distinct opens.
     */
    private val Intent?.pushDeliveryId: String?
        get() {
            val deliveryId = this?.getStringExtra(PACKAGE_PREFIX + TRACKING_PARAMETER)
                ?.takeIf { it.isNotEmpty() }
                ?.let { trackingPayload ->
                    runCatching { JSONObject(trackingPayload).optString(PUSH_DELIVERY_KEY) }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                }
            return deliveryId ?: this?.getStringExtra(Constants.NOTIFICATION_UID_EXTRA)
                ?.takeIf { it.isNotEmpty() }
        }

    /**
     * Appends Klaviyo extras from an intent to this event, parsing special fields as needed
     */
    private fun Event.appendKlaviyoExtras(intent: Intent?) {
        intent?.extras?.keySet()?.forEach { key ->
            if (key.contains(PACKAGE_PREFIX)) {
                val eventKey = EventKey.CUSTOM(key.replace(PACKAGE_PREFIX, ""))
                val rawValue = intent.extras?.getString(key, "") ?: ""
                val parsedValue = when (eventKey.name) {
                    KEY_VALUE_PAIRS -> parseKeyValuePairs(rawValue) ?: return@forEach
                    else -> rawValue
                }

                this[eventKey] = parsedValue
            }
        }
    }

    /**
     * Decodes a push's key/value pairs JSON into a map, or null (logging a warning that carries no
     * part of the value) when it is not a JSON object.
     */
    private fun parseKeyValuePairs(raw: String): HashMap<String, Serializable?>? = try {
        JSONObject(FormsPreview.keyValuePairsWithoutPreviewToken(raw)).toHashMap()
    } catch (e: Exception) {
        Registry.log.warning("Failed to parse $KEY_VALUE_PAIRS JSON")
        null
    }
}
