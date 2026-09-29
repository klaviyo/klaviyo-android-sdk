package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.StateKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest.Companion.HTTP_BAD_REQUEST
import com.klaviyo.analytics.networking.requests.KlaviyoErrorResponse
import com.klaviyo.analytics.networking.requests.KlaviyoErrorSource
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.PushTokenFetcher
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.safeApply
import com.klaviyo.core.utils.takeIf

internal class StateSideEffects(
    private val state: State = Registry.get<State>(),
    private val apiClient: ApiClient = Registry.get<ApiClient>(),
    private val lifecycleMonitor: LifecycleMonitor = Registry.lifecycleMonitor
) {
    private val profileLock = Any()
    private var nextProfileRevision = 0L
    private var appliedProfileRevision = 0L
    private var timerGeneration = 0L

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
        // Clear event buffer to prevent cross-account data leakage
        GenericEventBuffer.clearBuffer()

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

    private fun onUserStateChange(requireAttributes: Boolean = false) {
        val revision = synchronized(profileLock) { ++nextProfileRevision }
        val profile = state.getAsProfile(withAttributes = true)
        if (requireAttributes && profile.attributes.propertyCount() == 0) return

        val outgoing = synchronized(profileLock) {
            if (revision < appliedProfileRevision) return
            appliedProfileRevision = revision
            val previous = pendingProfile?.takeIf { it.anonymousId != profile.anonymousId }
                ?.let { takePendingProfileLocked() }

            Registry.log.verbose(
                "${pendingProfile?.let { "Merging" } ?: "Starting"} profile update"
            )

            // Merge changes into pending transaction, or start a new one
            pendingProfile = pendingProfile?.copy()?.mergeWithCurrentIdentifiers(profile) ?: profile

            // Reset timer
            timer?.cancel()
            val generation = ++timerGeneration
            timer = Registry.clock.schedule(Registry.config.debounceInterval.toLong()) {
                flushProfile(generation)
            }
            previous
        }
        outgoing?.let { enqueueTokenOrProfile(it) }
    }

    /**
     * Enqueue pending profile changes as an API call and then clear slate
     */
    private fun flushProfile(generation: Long) {
        val profile = synchronized(profileLock) {
            if (generation != timerGeneration) return
            takePendingProfileLocked()
        } ?: return
        enqueueTokenOrProfile(profile)
    }

    private fun takePendingProfileLocked(): Profile? = pendingProfile?.copy()?.also {
        timer?.cancel()
        timer = null
        timerGeneration++
        pendingProfile = null
        Registry.log.verbose("Flushing profile update")
        state.resetAttributes() // Once captured in a request, we don't keep profile attributes in state/on disk
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

    private fun Profile.mergeWithCurrentIdentifiers(current: Profile): Profile = merge(current).apply {
        externalId = current.externalId
        email = current.email
        phoneNumber = current.phoneNumber
        anonymousId = current.anonymousId
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

        is StateChange.ProfileIdentifier,
        is StateChange.ProfileReset -> {
            onUserStateChange()
        }

        is StateChange.ProfileAttributes -> onUserStateChange(requireAttributes = true)

        is StateChange.KeyValue -> when (change.key) {
            StateKey.PUSH_STATE -> onPushStateChange()
            ProfileKey.PUSH_TOKEN -> Unit /* Token is a no-op, push changes are captured by push state */
            else -> Unit
        }
    }

    /**
     * Bring push state up to date on every resume, via exactly one path.
     *
     * Two things can go stale while backgrounded (or merely occluded): the push token (the
     * provider may rotate it) and the device properties embedded in push state (notification
     * permission, background availability). A successful token fetch already covers both, because
     * assigning the token recomputes push state — so the fetch is attempted first, and a direct
     * refresh runs only when no fetch will deliver one (forwarding disabled, `push-fcm` absent, or
     * the fetch failed).
     *
     * Doing both unconditionally would double-report: when the token *and* a device property have
     * both changed, refreshing from the token already in state enqueues a push-token request
     * carrying the token the provider has already rotated away from, which the newly fetched token
     * then immediately supersedes — two requests for one foreground, the first already stale.
     *
     * A system permission dialog (e.g. POST_NOTIFICATIONS) runs in a different process and only
     * pauses the host activity — no onStop/onStart pair — so a hook gated on a fresh foreground
     * (activeActivities == 0) never fires when such a dialog is dismissed, and permission changes
     * go undetected until a true background/foreground cycle.
     */
    private fun onLifecycleEvent(activity: ActivityEvent) {
        activity.takeIf<ActivityEvent.Resumed>()?.run {
            val refreshFromStoredToken: () -> Unit = { safeApply { state.refreshPushState() } }

            if (!PushTokenFetcher.maybeAutoRegisterPushToken(refreshFromStoredToken)) {
                refreshFromStoredToken()
            }
        }
    }
}
