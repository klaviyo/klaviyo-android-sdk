package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.Profile
import java.io.Serializable
import org.json.JSONObject

internal class PersistentObservableProfile(
    key: Keyword,
    onChanged: (PersistentObservableProperty<ImmutableProfile?>) -> Unit
) : PersistentObservableProperty<ImmutableProfile?>(
    key = key,
    default = null,
    onChanged = onChanged
) {
    override fun serialize(value: ImmutableProfile?): String = value?.toString() ?: ""

    override fun deserialize(storedValue: String): ImmutableProfile? = storedValue.takeIf { it != "" }?.let {
        JSONObject(storedValue)
    }?.let { json ->
        Profile().apply {
            // TODO is `as Serializable` safe?
            // TODO catching / type safety
            json.keys().forEach { setProperty(it, json.get(it) as Serializable) }
        }
    }
}
