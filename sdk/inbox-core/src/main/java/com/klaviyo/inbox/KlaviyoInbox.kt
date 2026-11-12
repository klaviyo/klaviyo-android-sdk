package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
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
     * posted it, so each callback checks [isActive] when it runs. Guarded by [listeners].
     */
    private class ChangeSubscription {
        var isActive = true
        var job: Job? = null
    }

    private val listeners = mutableMapOf<InboxChangeListener, ChangeSubscription>()

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
     * Calls [listener] on the main thread whenever the inbox's messages change, until it is
     * removed. Adding a listener that is already added does nothing. Hold the listener and remove
     * it when the host no longer shows the inbox, since the SDK keeps a reference until then.
     *
     * The listener hears changes made after this returns, so call this before fetching.
     *
     * @see MobileInbox.changes
     */
    @JvmStatic
    fun addChangeListener(listener: InboxChangeListener) {
        val inbox = Klaviyo.mobileInbox
        synchronized(listeners) {
            if (listeners.containsKey(listener)) return
            val subscription = ChangeSubscription().also { listeners[listener] = it }
            // Undispatched, so the collection is subscribed before this returns
            subscription.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    inbox.changes.collect {
                        Registry.threadHelper.runOnUiThread {
                            if (synchronized(listeners) { subscription.isActive }) {
                                listener.onChange()
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Registry.log.error("Mobile Inbox change listener stopped", e)
                }
            }
        }
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
        synchronized(listeners) {
            listeners.remove(listener)?.also { it.isActive = false }
        }?.job?.cancel()
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
