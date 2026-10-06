package com.klaviyo.inbox

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.safeLaunch
import com.klaviyo.inbox.InboxPushNormalizer.toInboxCaptureRecord
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope

/**
 * Copies delivered Klaviyo pushes into the inbox when capture is enabled.
 *
 * Runs on the FCM worker thread before display: the gate and normalization run inline, and the
 * store write runs in the background. Every failure is logged and leaves display unaffected.
 * Push dispatches regardless of notification permission; [isInboxCaptureEnabled] skips capture
 * when permission isn't granted.
 */
internal object InboxCaptureObserver : (RemoteMessage) -> Unit {

    override fun invoke(message: RemoteMessage) {
        try {
            if (!isInboxCaptureEnabled()) return

            val record = message.toInboxCaptureRecord(Registry.clock.currentTimeMillis()) ?: run {
                Registry.log.debug("Mobile Inbox skipped a push with nothing to deduplicate on")
                return
            }

            val repository = Registry.getOrNull<InboxRepository>() ?: run {
                Registry.log.verbose("Mobile Inbox store unavailable; push not captured")
                return
            }

            CoroutineScope(Registry.dispatcher).safeLaunch {
                try {
                    when (val result = repository.capture(record)) {
                        InboxCaptureResult.FAILED -> Registry.log.warning(
                            "Mobile Inbox failed to store a push"
                        )
                        else -> Registry.log.verbose("Mobile Inbox capture result: $result")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Registry.log.error("Mobile Inbox failed to store a push", e)
                }
            }
        } catch (e: Exception) {
            Registry.log.error("Mobile Inbox failed to capture a push", e)
        }
    }
}
