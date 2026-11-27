package com.klaviyo.core.model

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.klaviyo.core.Registry
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Simple DataStore implementation using SharedPreferences for persistence
 *
 * To keep things very simple, this interface only expects a key/value storage with strings.
 * JSON-encoding is the simplest way to leverage this store with non-string data,
 * which of course means accessors must implement type safety checks as necessary.
 */
internal class SharedPreferencesDataStore : DataStore {

    @Volatile
    private var applicationContext: Context? = null

    internal fun bindContext(context: Context) {
        if (applicationContext == null) applicationContext = context.applicationContext ?: context
    }

    /**
     * List of registered observers
     */
    private val storeObservers = CopyOnWriteArrayList<StoreObserver>()

    override fun onStoreChange(observer: StoreObserver) {
        storeObservers += observer
    }

    override fun offStoreChange(observer: StoreObserver) {
        storeObservers -= observer
    }

    private fun broadcastStoreChange(key: String, value: String?) {
        Registry.log.verbose("$key=$value")
        storeObservers.forEach { observer ->
            try {
                observer(key, value)
            } catch (e: Exception) {
                Registry.log.error("Store change observer failed", e)
            }
        }
    }

    /**
     * Opens the Klaviyo SDK's shared preferences file
     *
     * @return The Klaviyo SDK's shared preferences opened in private mode
     */
    private fun openSharedPreferences(): SharedPreferences {
        return (applicationContext ?: Registry.config.applicationContext).getSharedPreferences(
            KLAVIYO_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    /**
     * Opens the shared preferences and writes a given key/value pair
     *
     * The write operation is performed async, but has no callback
     *
     * @param key The identifying key that the value being written will go by
     * @param value The value that we are writing to the shared preferences
     */
    override fun store(key: String, value: String) {
        openSharedPreferences()
            .edit()
            .putString(key, value)
            .apply()
            .also {
                broadcastStoreChange(key, value)
            }
    }

    override fun commit(key: String, value: String?): Boolean {
        val editor = openSharedPreferences().edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            // Android 7.1 and earlier skip disk writes for unchanged preference maps.
            editor.putString(CHECKED_COMMIT_REVISION_KEY, UUID.randomUUID().toString())
        }
        return editor.commit().also { if (it) broadcastStoreChange(key, value) }
    }

    /**
     * Opens the shared preferences and reads the value of a given key
     *
     * @param key The identifying key of the value we want to read
     *
     * @return The value read from the shared preferences for the given key
     */
    override fun fetch(key: String): String? {
        return openSharedPreferences().getString(key, null)
    }

    /**
     * Remove a value from shared preferences if set
     *
     * @param key The identifying key to remove from persistent store
     */
    override fun clear(key: String) {
        openSharedPreferences()
            .edit()
            .remove(key)
            .apply()
            .also {
                broadcastStoreChange(key, null)
            }
    }

    /**
     * Remove multiple keys from shared preferences in a single edit
     *
     * [SharedPreferences.Editor.apply] writes to disk asynchronously, and copies the entire
     * preferences map on every call made while a prior write is still in flight. Removing keys
     * one edit at a time therefore costs a full-map copy per key.
     *
     * @param keys The identifying keys to remove from persistent store
     */
    override fun clear(keys: Collection<String>) {
        if (keys.isEmpty()) return

        val editor = openSharedPreferences().edit()
        keys.forEach(editor::remove)
        editor.apply()

        keys.forEach { key -> broadcastStoreChange(key, null) }
    }

    internal companion object {
        const val KLAVIYO_PREFS_NAME = "KlaviyoSDKPreferences"
        private const val CHECKED_COMMIT_REVISION_KEY = "__klaviyo_checked_commit_revision"
    }
}
