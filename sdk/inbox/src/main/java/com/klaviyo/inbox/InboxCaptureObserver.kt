package com.klaviyo.inbox

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.safeLaunch
import com.klaviyo.inbox.InboxPushNormalizer.toInboxCaptureRecord
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope

/**
 * Copies delivered Klaviyo pushes into the inbox when capture is enabled.
 *
 * Runs before display. The gate and normalization run inline, then the store write runs in the
 * background while this waits up to [CAPTURE_TIMEOUT_MS] for it, so the write finishes while the
 * push service still keeps the process alive. On timeout, display continues and the write carries
 * on in the background. It never waits on the main thread. Every failure is logged and leaves
 * display unaffected.
 *
 * Push dispatches regardless of notification permission; [isInboxCaptureEnabled] skips capture
 * when permission isn't granted.
 */
internal object InboxCaptureObserver : (RemoteMessage) -> Unit {

    /**
     * Longest that display waits for the store write
     */
    internal const val CAPTURE_TIMEOUT_MS = 1_000L

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

            val written = CountDownLatch(1)
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
