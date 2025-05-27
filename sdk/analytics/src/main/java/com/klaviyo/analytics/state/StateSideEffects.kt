package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest.Companion.HTTP_BAD_REQUEST
import com.klaviyo.analytics.networking.requests.KlaviyoErrorResponse
import com.klaviyo.analytics.networking.requests.KlaviyoErrorSource
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.LifecycleMonitor

internal class StateSideEffects(
    private val state: State = Registry.get<State>(),
    private val apiClient: ApiClient = Registry.get<ApiClient>(),
    private val lifecycleMonitor: LifecycleMonitor = Registry.lifecycleMonitor
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
        state.onStateChange(::onStateChange)
        lifecycleMonitor.onActivityEvent(::onLifecycleEvent)
    }

    /**
     * Detach side effects observers
     */
    fun detach() {
        apiClient.offApiRequest(::afterApiRequest)
        state.offStateChange(::onStateChange)
        lifecycleMonitor.offActivityEvent(::onLifecycleEvent)
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
        request.responseCode == HTTP_BAD_REQUEST -> {
            request.errorBody.errors.find { it.title == KlaviyoErrorResponse.INVALID_INPUT_TITLE }
                ?.let { inputError ->
                    val pointer = inputError.source?.pointer
                    when {
                        pointer?.contains(KlaviyoErrorSource.EMAIL_PATH) == true -> {
                            (Registry.get<State>() as? KlaviyoState)?.resetEmail().also {
                                Registry.log.warning(
                                    "Invalid email - resetting email state to null"
                                )
                            }
                        }

                        pointer?.contains(KlaviyoErrorSource.PHONE_NUMBER_PATH) == true -> {
                            (Registry.get<State>() as? KlaviyoState)?.resetPhoneNumber().also {
                                Registry.log.warning(
                                    "Invalid phone number - resetting phone number state to null"
                                )
                            }
                        }

                        else -> {
                            Registry.log.warning("Input error: ${inputError.detail}")
                        }
                    }
                }
        }

        request is PushTokenApiRequest && request.status == KlaviyoApiRequest.Status.Failed && request.responseCode != HTTP_BAD_REQUEST -> {
            state.pushState = null
        }

        else -> Unit
    }

    private fun onStateChange(change: StateChange) = when (change) {
        is StateChange.ApiKey -> {
            onApiKeyChange(oldApiKey = change.oldValue)
        }

        is StateChange.ProfileIdentifier, is StateChange.ProfileReset -> {
            onUserStateChange()
        }

        is StateChange.ProfileAttributes -> if (state.getAsProfile(withAttributes = true).attributes.propertyCount() > 0) {
            onUserStateChange()
        } else {
            Unit
        }

        is StateChange.KeyValue -> when (change.key) {
            ProfileKey.PUSH_STATE -> onPushStateChange()
            ProfileKey.PUSH_TOKEN -> Unit /* Token is a no-op, push changes are captured by push state */
            else -> Unit
        }
    }

    private fun onLifecycleEvent(activity: ActivityEvent): Unit = when {
        activity is ActivityEvent.Resumed -> Registry.get<State>().pushToken?.let {
            // This should trigger the token in state to refresh overall push state
            Registry.get<State>().pushToken = it
        } ?: Unit

        else -> Unit
    }
}
