package com.klaviyo.core

/**
 * The request surface a feature is reported on. Each request carrying the `X-Klaviyo-Sdk-Features`
 * header reports only the features in its scope, so unrelated domains never share a request.
 */
enum class SdkFeatureScope {
    PUSH_TOKEN_REGISTRATION
}

/**
 * Central catalog of reportable SDK features. Each entry pairs its wire name (serialized into the
 * header) with the manifest key it reads, its [scope], and how the raw manifest value maps to the
 * reported value. Adding a feature — including one reported on a different request — is a new entry
 * here; a feature is never serialized outside its [scope].
 */
enum class SdkFeatureKey(
    val wireName: String,
    val manifestKey: String,
    val scope: SdkFeatureScope,
    val reportedValue: (manifestValue: Boolean) -> Boolean = { it }
) {
    AUTO_PUSH_TRACKING(
        wireName = "auto_push_tracking",
        manifestKey = Constants.AUTOMATIC_PUSH_TRACKING,
        scope = SdkFeatureScope.PUSH_TOKEN_REGISTRATION
    ),

    // `disable_...` manifest key → reported as the inverse `auto_push_token_forwarding`. Reported
    // independently of the master flag, so the backend sees how the host set each flag.
    AUTO_PUSH_TOKEN_FORWARDING(
        wireName = "auto_push_token_forwarding",
        manifestKey = Constants.DISABLE_AUTOMATIC_TOKEN_FORWARDING,
        scope = SdkFeatureScope.PUSH_TOKEN_REGISTRATION,
        reportedValue = { disabled -> !disabled }
    )
}

/**
 * Serializes SDK feature-adoption flags into the `X-Klaviyo-Sdk-Features` header, used for SDK
 * adoption telemetry.
 *
 * Each flag is sourced from a manifest `<meta-data>` boolean and reported only when the host
 * actually declared that key, so the backend can distinguish "configured false" from "not
 * configured" (absent keys are omitted from the header).
 */
object SdkFeatures {
    /** Name of the HTTP header these features are serialized into. */
    const val HEADER_NAME = "X-Klaviyo-Sdk-Features"

    /**
     * Header value for the given [scope], e.g. `auto_push_tracking=1; auto_push_token_forwarding=0;`.
     * Includes only in-scope features whose manifest key is present; returns `null` (so callers omit
     * the header) when none are configured.
     */
    fun headerValue(scope: SdkFeatureScope): String? =
        SdkFeatureKey.entries
            .filter { it.scope == scope }
            .mapNotNull { key ->
                if (Registry.config.hasManifestKey(key.manifestKey)) {
                    val value = key.reportedValue(
                        Registry.config.getManifestBoolean(key.manifestKey, false)
                    )
                    "${key.wireName}=${if (value) "1" else "0"};"
                } else {
                    null
                }
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
}
