package com.klaviyo.sample

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a lightly-persisted history of the push notifications the sample app has received, so the
 * [PushLogView] screen can show each notification's title, body, and custom key-value pairs without
 * needing to reproduce the push or dig through Logcat. Mirrors the iOS example app's PushLogStore.
 *
 * The list is exposed as a [StateFlow] so Compose can observe it. Entries are recorded from
 * [SamplePushService] (a background FCM thread) and read from the UI thread; [MutableStateFlow]
 * handles that hand-off safely.
 */
object PushLogStore {
    private const val PREFS_NAME = "com.klaviyo.sample.pushLog"
    private const val STORAGE_KEY = "entries"
    private const val MAX_ENTRIES = 50

    private val _entries = MutableStateFlow<List<PushLogEntry>>(emptyList())
    val entries: StateFlow<List<PushLogEntry>> = _entries.asStateFlow()

    private var prefs: SharedPreferences? = null

    /**
     * Call once from Application.onCreate. Hydrates the in-memory list from disk (debug builds only).
     */
    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _entries.value = load()
    }

    fun record(
        source: PushLogEntry.Source,
        title: String,
        body: String,
        customData: Map<String, String>
    ) {
        val entry = PushLogEntry(
            id = UUID.randomUUID().toString(),
            receivedAt = System.currentTimeMillis(),
            source = source,
            title = title,
            body = body,
            customData = customData
        )
        _entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
        save()
    }

    fun clear() {
        _entries.value = emptyList()
        save()
    }

    private fun save() {
        // Push payloads can carry sensitive custom data (promo codes, identifiers, etc.), so only
        // persist across launches in debug builds. Release builds keep the log in memory for the
        // current session only.
        if (!BuildConfig.DEBUG) return
        prefs?.edit()?.putString(STORAGE_KEY, encode(_entries.value))?.apply()
    }

    private fun load(): List<PushLogEntry> {
        if (!BuildConfig.DEBUG) return emptyList()
        val json = prefs?.getString(STORAGE_KEY, null) ?: return emptyList()
        return decode(json)
    }

    // Serialize via org.json so the sample app needn't pull in a JSON/serialization dependency.

    private fun encode(entries: List<PushLogEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val custom = JSONObject()
            entry.customData.forEach { (key, value) -> custom.put(key, value) }
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("receivedAt", entry.receivedAt)
                    .put("source", entry.source.name)
                    .put("title", entry.title)
                    .put("body", entry.body)
                    .put("customData", custom)
            )
        }
        return array.toString()
    }

    private fun decode(json: String): List<PushLogEntry> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val customObj = obj.optJSONObject("customData") ?: JSONObject()
            val customData = customObj.keys().asSequence()
                .associateWith { key -> customObj.getString(key) }
            PushLogEntry(
                id = obj.getString("id"),
                receivedAt = obj.getLong("receivedAt"),
                source = runCatching { PushLogEntry.Source.valueOf(obj.getString("source")) }
                    .getOrDefault(PushLogEntry.Source.NOTIFICATION),
                title = obj.optString("title"),
                body = obj.optString("body"),
                customData = customData
            )
        }
    }.getOrDefault(emptyList())
}
