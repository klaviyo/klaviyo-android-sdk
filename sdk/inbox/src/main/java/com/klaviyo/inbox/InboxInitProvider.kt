package com.klaviyo.inbox

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.PushNotificationDispatcher

internal class InboxInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: run {
            Registry.log.error("No application context; Mobile Inbox capture is off")
            return true
        }
        Registry.registerOnce<InboxRepository> {
            KlaviyoInboxRepository(RoomInboxDataSource(appContext))
        }
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
