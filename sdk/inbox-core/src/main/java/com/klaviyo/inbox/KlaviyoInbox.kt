package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Java-friendly callback versions of [MobileInbox].
 * Kotlin users should use [Klaviyo.mobileInbox] instead.
 *
 * Callbacks run on the main thread.
 *
 * @throws MissingKlaviyoModule from every method if the `com.klaviyo:inbox` module is not on the
 *  classpath.
 */
object KlaviyoInbox {

    private val scope by lazy { CoroutineScope(SupervisorJob() + Registry.dispatcher) }

    /**
     * One registration of a listener. A callback posted to the main thread outlives the job that
     * posted it, so each callback checks [isActive] when it runs. Both fields are read and written
     * from several threads without a lock, so they are volatile.
     */
    private class ChangeSubscription {
        @Volatile var isActive = true

        @Volatile var job: Job? = null
    }

    // Typed as ConcurrentMap so putIfAbsent and remove(key, value) resolve to the API 1 methods
    private val listeners: ConcurrentMap<InboxChangeListener, ChangeSubscription> =
        ConcurrentHashMap()

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
        callback: InboxUpdateCallback
    ) = deliver(callback::onResult) { it.updateMessageState(messageId, change) }

    /**
     * @see MobileInbox.trackMessageClick
     */
    @JvmStatic
    @JvmOverloads
    fun trackMessageClick(
        message: InboxMessage,
        action: InboxAction? = null,
        properties: Map<String, Serializable?> = emptyMap()
    ) = Klaviyo.mobileInbox.trackMessageClick(message, action, properties)

    /**
     * Delivers how many messages changed.
     *
     * @see MobileInbox.updateMessageState
     */
    @JvmStatic
    fun updateMessageState(
        change: InboxStateChange,
        filter: InboxFilter,
        callback: InboxCountCallback
    ) = deliver(callback::onResult) { it.updateMessageState(change, filter) }

    /**
     * @see MobileInbox.clearAll
     */
    @JvmStatic
    fun clearAll(callback: InboxClearCallback) = deliver(callback::onResult) { it.clearAll() }

    /**
     * Calls [listener] on the main thread whenever the inbox's messages change, until it is
     * removed, or until listening fails, which is logged and frees the listener to be added again.
     * Adding a listener that is already added does nothing. Hold the listener and remove
     * it when the host no longer shows the inbox, since the SDK keeps a reference until then.
     *
     * The listener hears changes made after this returns, so call this before fetching.
     *
     * @see MobileInbox.changes
     */
    @JvmStatic
    fun addChangeListener(listener: InboxChangeListener) {
        val inbox = Klaviyo.mobileInbox
        val subscription = ChangeSubscription()
        if (listeners.putIfAbsent(listener, subscription) != null) return
        // Undispatched, so the collection is subscribed before this returns
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                inbox.changes.collect {
                    Registry.threadHelper.runOnUiThread {
                        if (subscription.isActive) listener.onChange()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Registry.log.error("Mobile Inbox change listener stopped", e)
                // Free the slot so the host can add the listener again. Only this
                // registration, in case the listener was already removed and re-added.
                subscription.isActive = false
                listeners.remove(listener, subscription)
            }
        }
        subscription.job = job
        // A remove that ran before the job was stored found nothing to cancel
        if (!subscription.isActive) job.cancel()
    }

    /**
     * Stops calling [listener], including for a change that is already waiting to be delivered to
     * the main thread. Does nothing if it was not added.
     *
     * Called on the main thread, [listener] is not called again once this returns. Called on
     * another thread, a call that has passed its check on the main thread can still start or finish
     * after this returns, and one that has not reached the check is dropped.
     */
    @JvmStatic
    fun removeChangeListener(listener: InboxChangeListener) {
        val subscription = listeners[listener] ?: return
        // Deactivate before removing, so a second remover that finds the entry gone cannot return
        // while the callbacks it is waiting on are still live
        subscription.isActive = false
        if (listeners.remove(listener, subscription)) subscription.job?.cancel()
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
                    InboxException.StorageFailure("Mobile Inbox call failed", e)
                )
            }
            Registry.threadHelper.runOnUiThread { callback(result) }
        }
    }
}
