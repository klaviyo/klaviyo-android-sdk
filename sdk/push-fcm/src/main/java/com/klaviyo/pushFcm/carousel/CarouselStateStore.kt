package com.klaviyo.pushFcm.carousel

import android.content.Context
import androidx.core.content.edit
import com.klaviyo.core.Registry
import org.json.JSONObject

/**
 * Persists the current card index of each carousel notification, keyed by notification tag.
 *
 * The carousel receiver reads the index from here rather than from the arrow intent, so the store
 * is the single source of truth: a stale intent cannot rewind a card. Stored as one JSON map,
 * `tag -> {index, updatedAt}`, in a dedicated prefs file. Entries older than [MAX_AGE_MS] are
 * pruned on every write so an abandoned carousel cannot pin storage forever.
 *
 * Every method takes the caller's [Context] and never touches [Registry.config] or
 * [Registry.dataStore]: both throw before `Klaviyo.initialize`, and both FCM receipt and arrow taps
 * can happen pre-initialization. Only [Registry.log] is safe to use here.
 */
internal object CarouselStateStore {
    /**
     * Dedicated prefs file, kept separate from the SDK's main data store so it needs no
     * initialized config to open.
     */
    private const val PREFS_NAME = "klaviyo_carousel_state"

    /**
     * Key holding the whole `tag -> {index, updatedAt}` map inside [PREFS_NAME].
     */
    private const val STATE_KEY = "carousel_state"

    /**
     * Age after which a carousel's entry is dropped. A carousel that was never dismissed and never
     * advanced past this point is stale; the next write prunes it.
     */
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private const val INDEX_KEY = "index"
    private const val UPDATED_AT_KEY = "updatedAt"

    /**
     * Read the current card index for [tag], defaulting to 0 (the first card) when no entry exists.
     *
     * @param context The caller's context; used only to open [PREFS_NAME].
     * @param tag The notification tag identifying the carousel.
     */
    fun getIndex(context: Context, tag: String): Int = load(context)[tag]
        ?.optInt(INDEX_KEY, 0)
        ?: 0

    /**
     * Record the current card index for [tag], pruning stale entries in the same write.
     *
     * @param context The caller's context; used only to open [PREFS_NAME].
     * @param tag The notification tag identifying the carousel.
     * @param index The 0-based index of the displayed card within the parsed card list.
     */
    fun setIndex(context: Context, tag: String, index: Int) {
        val now = System.currentTimeMillis()
        val map = load(context)
            .filterValues { it.optLong(UPDATED_AT_KEY, 0) > now - MAX_AGE_MS }
            .toMutableMap()
        map[tag] = JSONObject()
            .put(INDEX_KEY, index)
            .put(UPDATED_AT_KEY, now)
        save(context, map)
    }

    /**
     * Forget the carousel for [tag], called when the notification is dismissed.
     *
     * @param context The caller's context; used only to open [PREFS_NAME].
     * @param tag The notification tag identifying the carousel.
     */
    fun clear(context: Context, tag: String) {
        val map = load(context).toMutableMap()
        if (map.remove(tag) != null) {
            save(context, map)
        }
    }

    /**
     * Read and parse the whole tag map, returning an empty map when absent or corrupt.
     */
    private fun load(context: Context): Map<String, JSONObject> = try {
        preferences(context).getString(STATE_KEY, null)?.let { storedJson ->
            JSONObject(storedJson).let { json ->
                json.keys().asSequence().associateWith { key -> json.getJSONObject(key) }
            }
        } ?: emptyMap()
    } catch (e: Exception) {
        Registry.log.error("Failed to load carousel state", e)
        emptyMap()
    }

    /**
     * Serialise and persist the whole tag map.
     */
    private fun save(context: Context, map: Map<String, JSONObject>) {
        try {
            preferences(context).edit {
                putString(STATE_KEY, JSONObject(map).toString())
            }
        } catch (e: Exception) {
            Registry.log.error("Failed to save carousel state", e)
        }
    }

    /**
     * Open the dedicated carousel prefs file from the given context.
     */
    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
