package com.klaviyo.coresdk.config

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.klaviyo.coresdk.BuildConfig

/**
 * Exception that is thrown when the the Klaviyo API token is missing from the config
 */
class MissingAPIKey : Exception("You must declare an API key for the Klaviyo SDK")

/**
 * Exception that is thrown when the application context is missing from the config
 */
class MissingContext : Exception("You must add your application context to the Klaviyo SDK")

/**
 * Exception to throw when a permission is not declared for the application context
 *
 * @param permission
 */
class MissingPermission(permission: String) : Exception("You must declare $permission in your manifest to use the Klaviyo SDK")

/**
 * Stores all configuration related to the Klaviyo Android SDK.
 */
object KlaviyoConfig : Config {
    /**
     * Debounce time for fluent profile setter methods
     *
     * Reasoning: The debounce is only intended to merge chained profile updates into one API call
     */
    private const val DEBOUNCE_INTERVAL: Int = 100

    /**
     * Network request timeout duration
     *
     * Reasoning: Ten seconds accommodates a reasonable network latency
     * On a perfect connection our API calls should take around 1 second
     */
    private const val NETWORK_TIMEOUT_DEFAULT: Int = 10_000

    /**
     * Interval between flushing network queue, and the basis for retry with exponential backoff
     *
     * Reasoning: A 30 second interval should give radios time to go back to sleep between batches,
     * four retries with a typical backoff pattern would then be 30s, 60s, 3m, 12m.
     */
    private const val NETWORK_FLUSH_INTERVAL_DEFAULT: Int = 30_000

    /**
     * How many API requests can be enqueued before flush
     *
     * Reasoning: The goal of depth control is to limit duration that radios are active
     * if a typical request takes 1-3 seconds, this should ideally limit us to 30-90 seconds
     */
    private const val NETWORK_FLUSH_DEPTH_DEFAULT: Int = 25

    /**
     * How many retries to allow an API request before permanent failure
     *
     * Reasoning: Most likely the rate limit should be cleared within 2 retries with exp backoff.
     * However, I wanted some extra padding for edge cases, since the consequence is lost data.
     */
    private const val NETWORK_MAX_RETRIES_DEFAULT: Int = 4

    override val baseUrl: String = BuildConfig.KLAVIYO_SERVER_URL
    override lateinit var apiKey: String private set
    override lateinit var applicationContext: Context private set
    override var debounceInterval = DEBOUNCE_INTERVAL
        private set
    override var networkTimeout = NETWORK_TIMEOUT_DEFAULT
        private set
    override var networkFlushInterval = NETWORK_FLUSH_INTERVAL_DEFAULT
        private set
    override var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT
        private set
    override var networkMaxRetries = NETWORK_MAX_RETRIES_DEFAULT
        private set

    /**
     * Nested class to enable the builder pattern for easy declaration of custom configurations
     */
    class Builder : Config.Builder {
        private var apiKey: String = ""
        private var applicationContext: Context? = null
        private var debounceInterval: Int = DEBOUNCE_INTERVAL
        private var networkTimeout: Int = NETWORK_TIMEOUT_DEFAULT
        private var networkFlushInterval: Int = NETWORK_FLUSH_INTERVAL_DEFAULT
        private var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT
        private var networkMaxRetries = NETWORK_MAX_RETRIES_DEFAULT

        override fun apiKey(apiKey: String) = apply {
            this.apiKey = apiKey
        }

        override fun applicationContext(context: Context) = apply {
            this.applicationContext = context
        }

        override fun debounceInterval(debounceInterval: Int) = apply {
            if (debounceInterval >= 0) {
                this.debounceInterval = debounceInterval
            } else {
                // TODO Logging
            }
        }

        override fun networkTimeout(networkTimeout: Int) = apply {
            if (networkTimeout >= 0) {
                this.networkTimeout = networkTimeout
            } else {
                // TODO Logging
            }
        }

        override fun networkFlushInterval(networkFlushInterval: Int) = apply {
            if (networkFlushInterval >= 0) {
                this.networkFlushInterval = networkFlushInterval
            } else {
                // TODO Logging
            }
        }

        override fun networkFlushDepth(networkFlushDepth: Int) = apply {
            if (networkFlushDepth > 0) {
                this.networkFlushDepth = networkFlushDepth
            } else {
                // TODO Logging
            }
        }

        override fun networkMaxRetries(networkMaxRetries: Int) = apply {
            if (networkMaxRetries >= 0) {
                this.networkMaxRetries = networkMaxRetries
            } else {
                // TODO Logging
            }
        }

        override fun build(): Config {
            if (apiKey.isEmpty()) {
                throw MissingAPIKey()
            }
            if (applicationContext == null) {
                throw MissingContext()
            }

            val permissions = applicationContext!!.packageManager.getPackageInfoCompat(
                applicationContext!!.packageName, PackageManager.GET_PERMISSIONS
            ).requestedPermissions ?: emptyArray()

            if (Manifest.permission.ACCESS_NETWORK_STATE !in permissions) {
                throw MissingPermission(Manifest.permission.ACCESS_NETWORK_STATE)
            }

            KlaviyoConfig.apiKey = apiKey
            KlaviyoConfig.applicationContext = applicationContext as Context
            KlaviyoConfig.debounceInterval = debounceInterval
            KlaviyoConfig.networkTimeout = networkTimeout
            KlaviyoConfig.networkFlushInterval = networkFlushInterval
            KlaviyoConfig.networkFlushDepth = networkFlushDepth
            KlaviyoConfig.networkMaxRetries = networkMaxRetries

            return KlaviyoConfig
        }
    }
}

internal fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
    }
