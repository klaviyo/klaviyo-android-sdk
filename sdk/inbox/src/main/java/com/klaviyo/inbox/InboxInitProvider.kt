package com.klaviyo.inbox

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.klaviyo.analytics.state.StateChangeObservers
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.PushNotificationDispatcher

internal class InboxInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: run {
            Registry.log.error(
                "No application context; Mobile Inbox capture is off",
                IllegalStateException("InboxInitProvider has no application context")
            )
            return true
        }
        val dataSource = RoomInboxDataSource(appContext)
        Registry.registerOnce<InboxResetGate> {
            InboxResetGate(
                dataSource,
                PreferencesResetMarkerStore(Registry.dataStore(appContext)),
                Registry.dispatcher
            )
        }
        Registry.registerOnce<InboxRepository> {
            KlaviyoInboxRepository(dataSource, Registry.get<InboxResetGate>())
        }
        Registry.registerOnce<MobileInboxProvider> { KlaviyoMobileInboxProvider() }
        // Move service registration to Analytics startup if another module needs it.
        Registry.registerOnce<StateChangeObservers> { StateChangeObservers() }
        Registry.get<StateChangeObservers>().add(Registry.get<InboxResetGate>()::onStateChange)
        Registry.getOrNull<PushNotificationDispatcher>()
            ?.onKlaviyoNotification(InboxCaptureObserver(appContext))
            ?: Registry.log.warning(
                "Push notification dispatcher unavailable; Mobile Inbox cannot observe pushes"
            )
        return true
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0
}
