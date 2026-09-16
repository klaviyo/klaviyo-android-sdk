package com.klaviyo.core

import com.klaviyo.core.config.AutomaticPushTokenForwarding
import com.klaviyo.core.config.automaticPushTokenForwarding

/**
 * Pulls the current push token from the device's push provider (e.g. FCM) and forwards it to
 * Klaviyo. Implemented in `push-fcm` and resolved lazily via [Registry]; when that module is absent
 * the lookup is null, so automatic token registration is a no-op for analytics-only integrators.
 */
interface PushTokenFetcher {
    /**
     * Pull the current push token and forward it to Klaviyo. Must not throw — failures (e.g.
     * Firebase not configured) are logged and swallowed.
     *
     * @param onUnavailable Invoked when no token could be retrieved, whether the failure is
     * synchronous or arrives later on the provider's callback. Lets the caller fall back to
     * refreshing push state from the token already in state, so device-property changes are still
     * picked up when the provider is unavailable. Never invoked once a token has been forwarded.
     *
     * May be invoked **asynchronously**, after this method has returned and off the caller's
     * stack — so any guard wrapping the call site will no longer be in scope. Implementations
     * must contain failures it raises to honor the must-not-throw contract above, and callers
     * should not assume an enclosing `safeApply` will catch them.
     */
    fun fetchAndSetPushToken(onUnavailable: () -> Unit = {})

    companion object {
        /**
         * Automatic push token registration: pull the current token and forward it to Klaviyo. Called
         * on `Klaviyo.initialize` and on each app foreground so token rotations are picked up.
         *
         * Requires an explicit opt-in via [Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING] — see
         * [AutomaticPushTokenForwarding.fetchesProactively]. No-op when that key is absent or `false`,
         * or when `push-fcm` is absent. `KlaviyoPushService.onNewToken` reads the same key from its own
         * Context (safe before init) but stays enabled when the key is absent.
         *
         * Independent of [Constants.AUTOMATIC_PUSH_OPEN_TRACKING] (which gates only automatic open tracking) —
         * this flag alone controls token forwarding.
         *
         * @return whether a fetch was dispatched; callers refresh push state themselves when it was not.
         */
        fun maybeAutoRegisterPushToken(onUnavailable: () -> Unit = {}): Boolean = safeCall {
            // Reading Registry.config throws MissingConfig before Klaviyo.initialize. Contained
            // here rather than at the call sites so this reports "nothing was dispatched" instead
            // of making every caller guard a side effect it only opted into.
            val forwarding = Registry.config.automaticPushTokenForwarding()
            if (!forwarding.fetchesProactively) {
                Registry.log.verbose(
                    "Skipping automatic push token registration (automaticTokenForwarding=$forwarding)"
                )
                return@safeCall false
            }

            val fetcher = Registry.getOrNull<PushTokenFetcher>()
            if (fetcher == null) {
                Registry.log.verbose(
                    "No push token fetcher registered; skipping automatic registration"
                )
                return@safeCall false
            }

            Registry.log.debug("Automatically fetching push token")
            // Deliberately a separate runCatching rather than leaning on the enclosing safeCall:
            // safeCall re-throws non-Klaviyo exceptions in debug builds, and a fetcher failure
            // must never disrupt the caller in any build — it is an optional side effect.
            return@safeCall runCatching {
                fetcher.fetchAndSetPushToken(onUnavailable)
                true
            }.getOrElse {
                Registry.log.warning("Automatic push token fetch failed", it)
                false
            }
        } ?: false
    }
}
