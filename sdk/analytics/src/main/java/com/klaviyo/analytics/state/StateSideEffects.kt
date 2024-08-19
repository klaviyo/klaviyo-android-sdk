package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.API_KEY
import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.PROFILE_ATTRIBUTES
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock

internal class StateSideEffects(
    private val state: State = Registry.get<State>(),
    private val apiClient: ApiClient = Registry.get<ApiClient>()
) {
    /**
     * Debounce timer for enqueuing profile API calls
     */
    private var timer: Clock.Cancellable? = null

    /**
     * Pending batch of profile updates to be merged into one API call
     */
    private var pendingProfile: ImmutableProfile? = null

    init {
        apiClient.onApiRequest(false, ::afterApiRequest)
        state.onStateChange { key: Keyword?, oldValue: Any? ->
            when (key) {
                API_KEY -> onApiKeyChange(oldApiKey = oldValue?.toString())
                ProfileKey.PUSH_STATE -> onPushStateChange()
                ProfileKey.PUSH_TOKEN -> { /* Token is a no-op, push changes are captured by push state */
                }

                PROFILE_ATTRIBUTES -> if (state.getAsProfile(withAttributes = true).attributes.propertyCount() > 0) {
                    onUserStateChange()
                }
                else -> onUserStateChange()
            }
        }
    }

    private fun onPushStateChange() {
        if (!state.pushState.isNullOrEmpty()) {
            state.pushToken?.let { apiClient.enqueuePushToken(it, state.getAsProfile()) }
        }
    }

    private fun onApiKeyChange(oldApiKey: String?) {
        // If the API key changes, we need to unregister the push token on the previous API key then register the push token with the new API key
        if (!state.pushState.isNullOrEmpty()) {
            state.pushToken?.let {
                oldApiKey?.let { oldApiKey ->
                    apiClient.enqueueUnregisterPushToken(oldApiKey, it, state.getAsProfile())
                }
                apiClient.enqueuePushToken(it, state.getAsProfile())
            }
        }
    }

    private fun onUserStateChange() {
        val profile = state.getAsProfile(withAttributes = true)

        // Anonymous ID indicates a profile reset, we should flush any pending profile changes immediately
        pendingProfile?.takeIf { it.anonymousId != profile.anonymousId }?.also {
            flushProfile()
        }

        Registry.log.verbose("${pendingProfile?.let { "Merging" } ?: "Starting"} profile update")

        // Merge changes into pending transaction, or start a new one
        pendingProfile = pendingProfile?.copy()?.merge(profile) ?: profile

        // Reset timer
        timer?.cancel()
        timer = Registry.clock.schedule(Registry.config.debounceInterval.toLong()) {
            flushProfile()
        }
    }

    /**
     * Enqueue pending profile changes as an API call and then clear slate
     */
    private fun flushProfile() = pendingProfile?.let {
        timer?.cancel()
        Registry.log.verbose("Flushing profile update")
        enqueueTokenOrProfile(it.copy())
        state.resetAttributes() // Once captured in a request, we don't keep profile attributes in state/on disk
        pendingProfile = null
    }

    /**
     * Enqueue pending profile changes as an API call to either push token endpoint or profile endpoint.
     *
     * Why? - Profile changes are sent to push token API when there is a push token present in state.
     * This is done to avoid resetting the push token in state, making a profile request and then another
     * request to the push token endpoint to set the push token.
     *
     * By just using the push token API we can avoid the extra request and also ensure that the push token
     * is set on the new profile in Klaviyo.
     */
    private fun enqueueTokenOrProfile(profile: Profile) {
        state.pushToken?.let {
            apiClient.enqueuePushToken(it, profile)
        } ?: apiClient.enqueueProfile(profile)
    }

    private fun afterApiRequest(request: ApiRequest) = when {
        request is PushTokenApiRequest && request.status == KlaviyoApiRequest.Status.Failed -> {
            state.pushState = null
        }
        else -> Unit
    }
}