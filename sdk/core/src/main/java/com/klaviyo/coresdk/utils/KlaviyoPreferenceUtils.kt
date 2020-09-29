package com.klaviyo.coresdk.utils

import android.content.Context
import android.content.SharedPreferences
import com.klaviyo.coresdk.KlaviyoConfig
import java.util.*

object KlaviyoPreferenceUtils {
    private const val KLAVIYO_PREFS_NAME = "KlaviyoSDKPreferences"

    internal const val KLAVIYO_UUID_KEY = "UUID"

    private fun openSharedPreferences(): SharedPreferences {
        return KlaviyoConfig.applicationContext.getSharedPreferences(KLAVIYO_PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal fun readOrGenerateUUID(): String? {
        var uuid = readStringPreference(KLAVIYO_UUID_KEY)
        if (uuid.isNullOrEmpty()) {
            uuid = UUID.randomUUID().toString()
            writeStringPreference(KLAVIYO_UUID_KEY, uuid)
        }
        return uuid
    }

    fun writeStringPreference(key: String, value: String) {
        val editor = openSharedPreferences().edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun readStringPreference(key: String): String? {
        val preferences = openSharedPreferences()
        return preferences.getString(key, "")
    }
}