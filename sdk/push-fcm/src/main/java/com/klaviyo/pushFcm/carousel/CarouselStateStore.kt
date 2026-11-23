package com.klaviyo.pushFcm.carousel

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import com.klaviyo.core.Registry
import org.json.JSONObject

/**
 * Persists which carousel is showing under each notification tag, and its current card index.
 *
 * Stored as one JSON map, `tag -> {uid, index, updatedAt}`, in a dedicated prefs file. The `uid`
 * is the notification UID of the carousel last posted under the tag, so each entry belongs to one
 * generation: a re-send that reuses `notification_tag` posts a new UID, and an arrow tap or
 * dismissal from the replaced carousel no longer matches it. The receiver reads the index from
 * here rather than from the arrow intent, so a stale intent cannot rewind a card.
 *
 * Posting goes through [replace] (first post) and [advance] (arrow re-post), which run the
 * `notify` call and the state write under one lock. That keeps the tray and the store in step: a
 * re-post can never land after a newer carousel replaced it under the same tag.
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
     * Key holding the whole `tag -> {uid, index, updatedAt}` map inside [PREFS_NAME].
     */
    private const val STATE_KEY = "carousel_state"

    /**
     * Age after which an entry is dropped, used only when the active notifications cannot be
     * read. Otherwise [pruneInactive] keeps exactly the carousels still on screen, however old.
     */
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private const val UID_KEY = "uid"
    private const val INDEX_KEY = "index"
    private const val UPDATED_AT_KEY = "updatedAt"

    /**
     * Read the current card index of the carousel [uid] posted under [tag].
     *
     * @param context The caller's context; used only to open [PREFS_NAME].
     * @param tag The notification tag identifying the carousel.
     * @param uid The notification UID carried by the arrow intent.
     * @return The index; 0 when no entry exists; null when a newer carousel has replaced [uid]
     *  under [tag], so the tap is stale.
     */
    fun getIndex(context: Context, tag: String, uid: String): Int? = synchronized(this) {
        val entry = load(context)[tag] ?: return@synchronized 0
        entry.takeIf { it.optString(UID_KEY) == uid }?.optInt(INDEX_KEY, 0)
    }

    /**
     * Post a new carousel under [tag] at card 1, replacing whatever carousel the tag held.
     *
     * The previous carousel's index stays valid until [post] runs, so its arrows keep working while
     * this one's images download.
     *
     * @param context The caller's context; used only to open [PREFS_NAME].
     * @param tag The notification tag the carousel is posted under.
     * @param uid The new carousel's notification UID.
     * @param post Posts the notification. The entry is written only if it returns.
     * @return The UID of the carousel this one replaced, if any, so the caller can drop its images.
     */
    fun replace(context: Context, tag: String, uid: String, post: () -> Unit): String? =
        synchronized(this) {
            val map = load(context).toMutableMap()
            val replaced = map[tag]?.optString(UID_KEY)?.takeIf { it.isNotEmpty() && it != uid }
            post()
            map[tag] = entry(uid, 0)
            save(context, map)
            replaced
        }

    /**
     * Re-post the carousel [uid] under [tag] at [index], if it is still the one showing and still
     * on card [fromIndex].
     *
     * Does nothing when a newer carousel has replaced [uid] under the tag, when the notification is
     * no longer showing (dismissed while its image downloaded), or when another move has changed
     * the card since this one read [fromIndex] (a later tap finished first while this one waited on
     * a download). The index is written only after [post] returns, so a failed re-post leaves the
     * stored card unchanged.
     *
     * @param context The caller's context; used to read the active notifications and open
     *  [PREFS_NAME].
     * @param tag The notification tag identifying the carousel.
     * @param uid The notification UID carried by the arrow intent.
     * @param fromIndex The stored index the move started from, as returned by [getIndex].
     * @param index The 0-based index of the card being posted.
     * @param post Posts the notification.
     * @return Whether the notification was re-posted.
     */
    fun advance(
        context: Context,
        tag: String,
        uid: String,
        fromIndex: Int,
        index: Int,
        post: () -> Unit
    ): Boolean = synchronized(this) {
        val map = load(context).toMutableMap()
        val entry = map[tag]
        if (entry != null && entry.optString(UID_KEY) != uid) {
            Registry.log.verbose("Carousel under tag '$tag' was replaced; dropping re-post")
            return@synchronized false
        }
        if ((entry?.optInt(INDEX_KEY, 0) ?: 0) != fromIndex) {
            Registry.log.verbose("Carousel under tag '$tag' moved since this tap; dropping re-post")
            return@synchronized false
        }
        if (activeTags(context)?.contains(tag) == false) {
            Registry.log.verbose("Carousel under tag '$tag' is no longer showing; dropping re-post")
            return@synchronized false
        }
        post()
        map[tag] = entry(uid, index)
        save(context, map)
        true
    }

    /**
     * Forget the carousel [uid] under [tag], called when the notification is dismissed. A newer
     * carousel posted under the same tag keeps its entry.
     *
     * @param context The caller's context; used only to open [PREFS_NAME].
     * @param tag The notification tag identifying the carousel.
     * @param uid The dismissed carousel's notification UID.
     */
    fun clear(context: Context, tag: String, uid: String) = synchronized(this) {
        val map = load(context).toMutableMap()
        if (map[tag]?.optString(UID_KEY) == uid) {
            map.remove(tag)
            save(context, map)
        }
    }

    /**
     * Forget every carousel whose notification is no longer showing, and drop its cached images.
     *
     * A body tap auto-cancels the notification without firing its delete intent, so [clear] never
     * runs for it. Calling this before posting a new carousel keeps that leftover state bounded to
     * the carousels actually on screen. When the active notifications cannot be read, entries older
     * than [MAX_AGE_MS] are dropped instead.
     *
     * @param context The caller's context; used to read the active notifications and open
     *  [PREFS_NAME].
     */
    fun pruneInactive(context: Context) {
        val removed = synchronized(this) {
            // Read under the lock, like advance: a snapshot taken before a concurrent replace
            // posted its tag would otherwise delete that carousel's fresh entry.
            val activeTags = activeTags(context)
            val map = load(context)
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            val stale = map.filter { (tag, entry) ->
                activeTags?.let { tag !in it } ?: (entry.optLong(UPDATED_AT_KEY, 0) <= cutoff)
            }
            if (stale.isNotEmpty()) {
                save(context, map - stale.keys)
            }
            stale.values
        }
        removed.forEach { entry ->
            entry.optString(UID_KEY).takeIf { it.isNotEmpty() }?.let(CarouselImageLoader::evict)
        }
    }

    /**
     * Tags of this app's notifications currently showing, or null when they cannot be read.
     */
    private fun activeTags(context: Context): Set<String>? = try {
        context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.mapNotNullTo(mutableSetOf()) { it.tag }
    } catch (e: Exception) {
        Registry.log.warning("Could not read active notifications for carousels", e)
        null
    }

    private fun entry(uid: String, index: Int) = JSONObject()
        .put(UID_KEY, uid)
        .put(INDEX_KEY, index)
        .put(UPDATED_AT_KEY, System.currentTimeMillis())

    /**
     * Read and parse the whole tag map, returning an empty map when absent or unparseable and
     * skipping any single entry that is not a JSON object.
     */
    private fun load(context: Context): Map<String, JSONObject> = try {
        preferences(context).getString(STATE_KEY, null)?.let { storedJson ->
            JSONObject(storedJson).let { json ->
                // One unreadable entry is dropped on its own. Failing the whole map would make
                // the next write persist it empty and reset every other carousel to card 1.
                json.keys().asSequence()
                    .mapNotNull { key -> json.optJSONObject(key)?.let { key to it } }
                    .toMap()
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
