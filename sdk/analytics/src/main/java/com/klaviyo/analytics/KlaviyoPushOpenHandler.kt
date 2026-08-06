package com.klaviyo.analytics

import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Constants
import com.klaviyo.core.Constants.KEY_VALUE_PAIRS
import com.klaviyo.core.Constants.PACKAGE_PREFIX
import com.klaviyo.core.Constants.TRACKING_PARAMETER
import com.klaviyo.core.Operation
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply
import com.klaviyo.core.utils.BoundedIdSet
import com.klaviyo.core.utils.JSONUtil.toHashMap
import java.util.Queue
import org.json.JSONObject

internal object KlaviyoPushOpenHandler {

    /**
     * Push delivery IDs already handled within this process, so a single tap records one
     * `$opened_push`. See [handle] for how entries are matched and added.
     */
    private val handledPushDeliveries = BoundedIdSet()

    /**
     * Key within the `_k` tracking payload whose value uniquely identifies a single push delivery.
     */
    private const val PUSH_DELIVERY_KEY = "tm"

    /**
     * Core push-open handling: guards, event enqueue, notification dismissal, deep-link dispatch.
     * Called by [Klaviyo.handlePush]; not meant for direct use outside this module.
     *
     * @param dispatchDeepLink When false, every stage still runs except the final deep-link
     *  dispatch — including recording the delivery in [handledPushDeliveries], so a later call for
     *  the same delivery still short-circuits. Callers pass false when they take ownership of
     *  delivering the link themselves.
     */
    internal fun handle(
        intent: Intent?,
        preInitQueue: Queue<Operation<Unit>>,
        dispatchDeepLink: Boolean = true
    ) {
        if (intent == null || !Klaviyo.isKlaviyoNotificationIntent(intent)) {
            Registry.log.verbose("Non-Klaviyo intent ignored")
            return
        }

        // Dedup guard: track each push delivery at most once per process. The trampoline calls
        // handlePush and forwards the same intent to the host, so a leftover manual handlePush call
        // (or singleTask re-entry) would otherwise double-track one tap. The first call to see a
        // delivery records it and proceeds; a later call for the same delivery short-circuits — no
        // event, dismissal, or deep link.
        val deliveryId = intent.pushDeliveryId
        if (deliveryId != null && !handledPushDeliveries.markOnce(deliveryId)) {
            Registry.log.verbose("Ignoring duplicate push open")
            return
        }

        // Create and enqueue an $opened_push. safeApply(preInitQueue) buffers this for replay if
        // handlePush runs before initialize(), and guards against unexpected failures.
        safeApply(preInitQueue) {
            val state = Registry.get<State>()
            val event = Event(EventMetric.OPENED_PUSH)
            event.appendKlaviyoExtras(intent)
            state.pushToken?.let { event[EventKey.PUSH_TOKEN] = it }
            // Not using Klaviyo.createEvent here to avoid nesting safeApply calls
            state.createEvent(event, state.getAsProfile())
        }

        // Dismiss the notification if opened via an action button. Body taps auto-cancel via
        // setAutoCancel(true) on the builder; action button taps don't (standard Android behavior).
        safeApply {
            val notificationTag = intent.getStringExtra(Constants.NOTIFICATION_TAG_EXTRA)
            if (notificationTag != null) {
                NotificationManagerCompat
                    .from(Registry.config.applicationContext)
                    .cancel(notificationTag, Constants.NOTIFICATION_ID)
            }
        }

        if (!dispatchDeepLink) return

        // If the notification carries a deep link and a handler is registered, invoke it. Otherwise
        // do nothing — the host already received the appropriate intent.
        safeApply {
            val deepLink = intent.data
            if (deepLink != null && DeepLinking.isHandlerRegistered) {
                DeepLinking.handleDeepLink(deepLink)
            }
        }
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
                    KEY_VALUE_PAIRS -> {
                        try {
                            JSONObject(rawValue).toHashMap()
                        } catch (e: Exception) {
                            Registry.log.warning(
                                "Failed to parse $KEY_VALUE_PAIRS JSON: $rawValue",
                                e
                            )
                            rawValue
                        }
                    }

                    else -> rawValue
                }

                this[eventKey] = parsedValue
            }
        }
    }
}
