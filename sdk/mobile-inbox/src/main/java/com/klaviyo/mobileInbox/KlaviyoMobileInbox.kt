package com.klaviyo.mobileInbox

import android.content.Context
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Public entry point for the Klaviyo mobile inbox feature.
 *
 * Call [initialize] once during app startup (after Klaviyo SDK initialization).
 * Developers using FCM can extend [KlaviyoInboxPushService] to automatically capture
 * push notifications, or call [handlePushMessage] from their own push service.
 */
object KlaviyoMobileInbox {

    private var repository: InboxRepository? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initializes the mobile inbox.
     *
     * Must be called before any other [KlaviyoMobileInbox] methods. Sets up the local Room
     * database, wires the remote API service, and registers a lifecycle observer that triggers
     * a sync whenever the app resumes.
     *
     * @param context Application context.
     * @param inboxEndpointUrl Full URL of the remote inbox endpoint (placeholder until confirmed).
     */
    @JvmStatic
    fun initialize(context: Context, inboxEndpointUrl: String) {
        val db = InboxDatabase.getInstance(context)
        val dao = db.inboxMessageDao()
        val apiService = InboxApiServiceImpl(inboxEndpointUrl)
        repository = InboxRepositoryImpl(dao, apiService) {
            // TODO: populate from Klaviyo profile state once we confirm the identity API surface
            InboxProfileParams()
        }

        Registry.lifecycleMonitor.onActivityEvent { event ->
            if (event is ActivityEvent.Resumed) {
                coroutineScope.launch { sync() }
            }
        }

        Registry.log.info("KlaviyoMobileInbox initialized with endpoint: $inboxEndpointUrl")
    }

    /**
     * Stores an incoming Klaviyo push notification in the local inbox database.
     *
     * Call this from your push service's [KlaviyoInboxPushService.onKlaviyoNotificationMessageReceived]
     * override, or subclass [KlaviyoInboxPushService] to have it called automatically.
     *
     * @param message The [RemoteMessage] received from FCM.
     */
    @JvmStatic
    fun handlePushMessage(message: RemoteMessage) {
        val repo = repository ?: run {
            Registry.log.warning("KlaviyoMobileInbox.handlePushMessage called before initialize")
            return
        }

        val id = message.messageId ?: run {
            val fallback = UUID.randomUUID().toString()
            Registry.log.warning(
                "RemoteMessage.messageId is null, using generated fallback id: $fallback"
            )
            fallback
        }

        val entity = InboxMessageEntity(
            id = id,
            timestamp = System.currentTimeMillis(),
            title = with(message) { title } ?: "",
            body = with(message) { body } ?: "",
            status = InboxStatus.UNREAD,
            source = InboxSource.PUSH
        )

        coroutineScope.launch {
            repo.upsertFromPush(entity)
            Registry.log.verbose("Stored push notification in inbox: id=$id")
        }
    }

    /**
     * Returns a [Flow] that emits the current list of non-hidden inbox messages, ordered by
     * most recent first. Automatically re-emits whenever the local database changes (including
     * after a [sync]).
     */
    fun observeMessages(): Flow<List<InboxMessage>> {
        return repository?.observeMessages() ?: run {
            Registry.log.warning("KlaviyoMobileInbox.observeMessages called before initialize")
            emptyFlow()
        }
    }

    /**
     * Returns a [Flow] that emits the count of unread inbox messages.
     */
    fun observeUnreadCount(): Flow<Int> {
        return repository?.observeUnreadCount() ?: run {
            Registry.log.warning("KlaviyoMobileInbox.observeUnreadCount called before initialize")
            emptyFlow()
        }
    }

    /**
     * Fetches the latest inbox messages from the remote endpoint and merges them into the local
     * database. The [observeMessages] flow will re-emit automatically after the sync completes.
     *
     * Called automatically on every [ActivityEvent.Resumed] after [initialize].
     */
    @JvmStatic
    suspend fun sync() {
        repository?.sync() ?: Registry.log.warning(
            "KlaviyoMobileInbox.sync called before initialize"
        )
    }

    /**
     * Marks the message with the given [id] as read.
     */
    @JvmStatic
    suspend fun markRead(id: String) {
        repository?.markRead(id) ?: Registry.log.warning(
            "KlaviyoMobileInbox.markRead called before initialize"
        )
    }

    /**
     * Marks the message with the given [id] as hidden. Hidden messages are excluded from
     * [observeMessages] and the [observeUnreadCount].
     */
    @JvmStatic
    suspend fun markHidden(id: String) {
        repository?.markHidden(id) ?: Registry.log.warning(
            "KlaviyoMobileInbox.markHidden called before initialize"
        )
    }

    /**
     * Inserts a synthetic inbox message. Useful for development and testing when a real push
     * payload or remote endpoint is not yet available.
     */
    @JvmStatic
    fun injectTestMessage(title: String, body: String) {
        val repo = repository ?: run {
            Registry.log.warning("KlaviyoMobileInbox.injectTestMessage called before initialize")
            return
        }
        val entity = InboxMessageEntity(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            title = title,
            body = body,
            status = InboxStatus.UNREAD,
            source = InboxSource.PUSH
        )
        coroutineScope.launch {
            repo.upsertFromPush(entity)
            Registry.log.verbose("Injected test message: title=$title")
        }
    }
}
