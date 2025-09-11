package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.model.Keyword
import com.klaviyo.core.model.PersistentObservableSerializable
import com.klaviyo.core.model.PropertyObserver
import java.io.Serializable

internal class PersistentObservableProfile(
    key: Keyword,
    onChanged: PropertyObserver<ImmutableProfile?> = { _, _ -> }
) : PersistentObservableSerializable<ImmutableProfile>(
    key = key,
    onChanged = onChanged
) {

    override fun createInstance(): ImmutableProfile = Profile()

    override fun populateInstance(instance: ImmutableProfile, key: String, value: Serializable) {
        (instance as Profile)[key] = value
    }
}
