package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.PROFILE_ATTRIBUTES
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock

internal class SideEffectCoordinator(
    private val apiClient: ApiClient = Registry.get<ApiClient>(),
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
                ProfileKey.PUSH_TOKEN -> { /* Token is a no-op, push changes are captured by push state */ }
                PROFILE_ATTRIBUTES -> if (userState.get(true).attributes.propertyCount() > 0) {
                    onUserStateChange()
                }
                else -> onUserStateChange()
            }
        }
    }

    private fun onPushStateChange() {
        if (!userState.pushState.isNullOrEmpty()) {
            userState.pushToken?.let { apiClient.enqueuePushToken(it, userState.get()) }
        }
    }

    private fun onUserStateChange() {
        val profile = userState.get(true)

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
        apiClient.enqueueProfile(it.copy())
        userState.resetAttributes() // Once captured in a request, we don't keep profile attributes in state/on disk
        pendingProfile = null
    }

    private fun afterApiRequest(request: ApiRequest) = when {
        request is PushTokenApiRequest && request.status == KlaviyoApiRequest.Status.Failed -> {
            userState.pushState = ""
        }
        else -> Unit
    }
}
