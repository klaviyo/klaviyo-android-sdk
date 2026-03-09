package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import java.io.Serializable
import org.json.JSONArray
import org.json.JSONObject

internal class PersistentObservableProfile(
    key: Keyword,
    onChanged: PropertyObserver<ImmutableProfile?> = { _, _ -> }
) : PersistentObservableProperty<ImmutableProfile?>(
    key = key,
    onChanged = onChanged
) {

    /**
     * Decode a JSON string to [Profile]
     */
    override fun deserialize(storedValue: String?): ImmutableProfile? = storedValue
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            try {
                val json = JSONObject(storedValue)
                Profile().also { profile ->
                    json.keys().forEach { key ->
                        profile[key] = deserializeValue(json.get(key))
                    }
                }
            } catch (e: Exception) {
                Registry.log.warning("Invalid stored JSON for $key", e)
                null
            }
        }

    /**
     * Recursively decode JSON into [Serializable] for type-safety
     * when re-populating a [Profile]
     */
    private fun deserializeValue(v: Any): Serializable = when (v) {
        is JSONArray -> Array(v.length()) { deserializeValue(v[it]) }
        is JSONObject -> HashMap<String, Serializable>(v.length()).also { map ->
            v.keys().forEach { key ->
                map[key] = deserializeValue(v.get(key))
            }
        }
        else -> v as Serializable
    }
}
