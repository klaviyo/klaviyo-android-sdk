package com.klaviyo.inbox

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.safeLaunch
import com.klaviyo.inbox.InboxPushNormalizer.toInboxMessageEntity
import com.klaviyo.pushFcm.KlaviyoNotificationObserver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope

/**
 * Copies delivered Klaviyo pushes into the inbox.
 *
 * Runs before display. The permission check and normalization run inline, then the store write runs in the
 * background while this waits up to [CAPTURE_TIMEOUT_MS] for it, so the write finishes while the
 * push service still keeps the process alive. On timeout, display continues and the write carries
 * on in the background. It never waits on the main thread. Every failure is logged and leaves
 * display unaffected.
 *
 * Push dispatches regardless of notification permission; capture is skipped when permission
 * isn't granted.
 *
 * Capture does not depend on `Klaviyo.initialize`: the company comes from the push payload and the
 * context from the host process, so a push that arrives first is still captured.
 *
 * @param context Application context used to read notification permission
 */
internal class InboxCaptureObserver(private val context: Context) : KlaviyoNotificationObserver {

    internal companion object {
        /**
         * Longest that display waits for the store write
         */
        const val CAPTURE_TIMEOUT_MS = 1_000L
    }

    override fun onNotification(message: RemoteMessage) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

            val entity = message.toInboxMessageEntity(
                receivedAt = Registry.clock.currentTimeMillis()
            ) ?: run {
                Registry.log.debug(
                    "Mobile Inbox skipped a push without a transmission or company ID"
                )
                return
            }

            val repository = Registry.getOrNull<InboxRepository>() ?: run {
                Registry.log.verbose("Mobile Inbox store unavailable; push not captured")
                return
            }

            val written = CountDownLatch(1)
            CoroutineScope(Registry.dispatcher).safeLaunch {
                try {
                    val result = repository.capture(entity)
                    Registry.log.verbose("Mobile Inbox capture result: $result")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Registry.log.error("Mobile Inbox failed to store a push", e)
                } finally {
                    written.countDown()
                }
            }

            awaitWrite(written)
        } catch (e: Exception) {
            Registry.log.error("Mobile Inbox failed to capture a push", e)
        }
    }

    private fun awaitWrite(written: CountDownLatch) {
        if (Registry.threadHelper.isMainThread()) {
            Registry.log.verbose(
                "Mobile Inbox capture continues in the background on the main thread"
            )
            return
        }
        if (!written.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Registry.log.warning(
                "Mobile Inbox store write exceeded ${CAPTURE_TIMEOUT_MS}ms; displaying without waiting"
            )
        }
    }
}
