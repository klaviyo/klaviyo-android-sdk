package com.klaviyo.forms

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.klaviyo.core.Registry

internal class FormsInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        Registry.registerOnce<FormsProvider> { KlaviyoFormsProvider() }
        return true
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0
}
