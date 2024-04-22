package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock

internal class SideEffectCoordinator(
    apiClient: ApiClient = Registry.get<ApiClient>(),
    private val userState: UserState = Registry.get<UserState>()
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
        userState.onStateChange { key: Keyword? ->
            when (key) {
                ProfileKey.PUSH_STATE -> onPushStateChange()
                else -> onUserStateChange()
            }
        }
    }

    private fun onPushStateChange() {
        if (userState.pushState.isNotEmpty()) {
            Registry.get<ApiClient>().enqueuePushToken(userState.pushToken, userState.get())
        }
    }

    private fun onUserStateChange() {
        val profile = userState.get(true)

        // Anonymous ID indicates a profile reset, we should flush any pending profile changes immediately
        pendingProfile?.takeIf { it.anonymousId != profile.anonymousId }?.also {
            flushProfile()
        }

        Registry.log.verbose("${pendingProfile?.let { "Merging" } ?: "Starting"} profile update")

        // Merge new changes into pending transaction and
        // add current identifiers from state to the pending transaction
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
        Registry.get<ApiClient>().enqueueProfile(it.copy())
        pendingProfile = null
    }

    private fun afterApiRequest(request: ApiRequest) = when {
        request is PushTokenApiRequest && request.status == KlaviyoApiRequest.Status.Failed -> {
            userState.pushState = ""
        }
        else -> Unit
    }
}
