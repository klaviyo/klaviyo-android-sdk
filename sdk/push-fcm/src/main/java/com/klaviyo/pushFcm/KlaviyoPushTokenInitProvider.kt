package com.klaviyo.pushFcm

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.klaviyo.core.PushTokenFetcher
import com.klaviyo.core.Registry

/**
 * Manifest-merged init [ContentProvider] that registers [KlaviyoPushTokenFetcher] at process start,
 * mirroring the forms module's `FormsInitProvider`. Lets analytics resolve a push-token fetcher
 * lazily via [Registry] without analytics depending on `push-fcm` or Firebase.
 */
internal class KlaviyoPushTokenInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Registry.registerOnce<PushTokenFetcher> { KlaviyoPushTokenFetcher() }
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
