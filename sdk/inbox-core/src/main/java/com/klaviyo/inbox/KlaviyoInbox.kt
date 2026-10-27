package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Java-friendly callback versions of [MobileInbox].
 * Kotlin users should use [Klaviyo.mobileInbox] instead.
 *
 * Callbacks and listeners run on the main thread.
 *
 * @throws MissingKlaviyoModule from every method if the `com.klaviyo:inbox` module is not on the
 *  classpath.
 */
object KlaviyoInbox {

    private val scope by lazy { CoroutineScope(SupervisorJob() + Registry.dispatcher) }
    private val listenerJobs = HashMap<InboxChangeListener, Job>()

    /**
     * @see MobileInbox.fetchPage
     */
    @JvmStatic
    @JvmOverloads
    fun fetchPage(query: InboxQuery = InboxQuery(), callback: InboxPageCallback) =
        deliver(callback::onResult) { it.fetchPage(query) }

    /**
     * @see MobileInbox.fetchUnreadCount
     */
    @JvmStatic
    fun fetchUnreadCount(callback: InboxCountCallback) =
        deliver(callback::onResult) { it.fetchUnreadCount() }

    /**
     * @see MobileInbox.updateMessageState
     */
    @JvmStatic
    fun updateMessageState(
        messageId: String,
        change: InboxStateChange,
        callback: InboxResultCallback
    ) = deliver(callback::onResult) { it.updateMessageState(messageId, change) }

    /**
     * Be notified when the inbox changes. Adding a listener that is already added does nothing.
     *
     * @see MobileInbox.changes
     */
    @JvmStatic
    fun addChangeListener(listener: InboxChangeListener) {
        val inbox = Klaviyo.mobileInbox
        synchronized(listenerJobs) {
            if (listenerJobs.containsKey(listener)) return
            listenerJobs[listener] = scope.launch {
                inbox.changes.collect {
                    Registry.threadHelper.runOnUiThread { listener.onInboxChanged() }
                }
            }
        }
    }

    @JvmStatic
    fun removeChangeListener(listener: InboxChangeListener) {
        synchronized(listenerJobs) { listenerJobs.remove(listener) }?.cancel()
    }

    private fun <T> deliver(
        callback: (InboxResult<T>) -> Unit,
        call: suspend (MobileInbox) -> T
    ) {
        val inbox = Klaviyo.mobileInbox
        scope.launch {
            val result = try {
                InboxResult.Success(call(inbox))
            } catch (e: CancellationException) {
                throw e
            } catch (e: InboxException) {
                InboxResult.Failure(e)
            } catch (e: Exception) {
                Registry.log.error("Mobile Inbox call failed", e)
                InboxResult.Failure(
                    InboxException(
                        InboxException.Reason.STORAGE_FAILURE,
                        "Mobile Inbox call failed",
                        e
                    )
                )
            }
            Registry.threadHelper.runOnUiThread { callback(result) }
        }
    }
}
