package com.klaviyo.pushFcm

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.klaviyo.core.PushTokenFetcher
import com.klaviyo.core.Registry

/**
 * Manifest-merged init [ContentProvider] that registers push-fcm services at process start,
 * mirroring the forms module's `FormsInitProvider`:
 * - [KlaviyoPushTokenFetcher], so analytics can resolve a push-token fetcher without depending
 *   on `push-fcm` or Firebase
 * - [KlaviyoPushNotificationDispatcher], so other modules can observe notifications before display
 *
 * Its manifest `initOrder` runs it before providers of modules that read these services.
 */
internal class KlaviyoPushTokenInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Registry.registerOnce<PushTokenFetcher> { KlaviyoPushTokenFetcher() }
        Registry.registerOnce<PushNotificationDispatcher> { KlaviyoPushNotificationDispatcher() }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}
