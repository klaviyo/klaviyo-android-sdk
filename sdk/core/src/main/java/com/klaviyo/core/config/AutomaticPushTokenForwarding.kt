package com.klaviyo.core.config

import android.content.Context
import com.klaviyo.core.Constants

/**
 * Resolved state of the [Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING] manifest flag, which is
 * three-valued: an absent key is distinct from an explicit `false`.
 *
 * The SDK has two independent automatic-forwarding paths, and each state selects a combination of
 * them via [forwardsOnTokenRotation] and [fetchesProactively]. Call sites read those properties
 * rather than comparing enum entries.
 */
enum class AutomaticPushTokenForwarding {
    /** Manifest key absent. */
    UNSET,

    /** Manifest key present and `true`. */
    ENABLED,

    /** Manifest key present and `false`. */
    DISABLED;

    /**
     * Whether to forward a token the push provider delivers to `KlaviyoPushService.onNewToken`.
     * Enabled unless forwarding is explicitly disabled.
     */
    val forwardsOnTokenRotation: Boolean get() = this != DISABLED

    /**
     * Whether to pull the current token from the provider at `Klaviyo.initialize` and on each
     * foreground, via `PushTokenFetcher.maybeAutoRegisterPushToken`. Requires an explicit opt-in.
     */
    val fetchesProactively: Boolean get() = this == ENABLED

    internal companion object {
        fun of(hasKey: Boolean, value: Boolean): AutomaticPushTokenForwarding = when {
            !hasKey -> UNSET
            value -> ENABLED
            else -> DISABLED
        }
    }
}

/**
 * Read [AutomaticPushTokenForwarding] from this [Context]'s manifest metadata.
 *
 * Safe before `Klaviyo.initialize`, unlike the [Config] overload, so `KlaviyoPushService` can
 * resolve the flag when the push provider delivers a token before the host initializes the SDK.
 */
fun Context.automaticPushTokenForwarding(): AutomaticPushTokenForwarding =
    AutomaticPushTokenForwarding.of(
        hasManifestKey(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING),
        getManifestBoolean(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING, false)
    )

/**
 * Read [AutomaticPushTokenForwarding] from the configured application context's manifest metadata.
 *
 * Resolves to [AutomaticPushTokenForwarding.UNSET] if no application context has been configured.
 */
fun Config.automaticPushTokenForwarding(): AutomaticPushTokenForwarding =
    AutomaticPushTokenForwarding.of(
        hasManifestKey(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING),
        getManifestBoolean(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING, false)
    )
