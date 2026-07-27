package com.klaviyo.core

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
     */
    fun fetchAndSetPushToken(onUnavailable: () -> Unit = {})

    companion object {
        /**
         * Automatic push token registration (via [Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING], default
         * [Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING_DEFAULT]): pull the current token and forward
         * it to Klaviyo. Called on `Klaviyo.initialize` and on each app foreground so token rotations
         * are picked up. No-op when the flag is explicitly off or when `push-fcm` is absent.
         *
         * Reads the flag and default that also govern `KlaviyoPushService.onNewToken` (which reads them
         * from its own Context, safe before init), so the two automatic-collection paths can't drift.
         *
         * Independent of [Constants.AUTOMATIC_PUSH_OPEN_TRACKING] (which gates only automatic open tracking) —
         * this flag alone controls token forwarding.
         */
        fun maybeAutoRegisterPushToken(onUnavailable: () -> Unit = {}): Boolean {
            // Must be called after Klaviyo.initialize, so Registry.config is available
            val forwardingEnabled = Registry.config.getManifestBoolean(
                Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING,
                Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING_DEFAULT
            )
            if (!forwardingEnabled) {
                Registry.log.verbose(
                    "Skipping automatic push token registration (automaticTokenForwarding=false)"
                )
                return false
            }

            val fetcher = Registry.getOrNull<PushTokenFetcher>() ?: run {
                Registry.log.verbose(
                    "No push token fetcher registered; skipping automatic registration"
                )
                return false
            }

            Registry.log.debug("Automatically fetching push token")
            // Contain any fetcher failure so this optional side effect cannot disrupt the caller
            return runCatching {
                fetcher.fetchAndSetPushToken(onUnavailable)
                true
            }.getOrElse {
                Registry.log.warning("Automatic push token fetch failed", it)
                false
            }
        }
    }
}
