package com.klaviyo.coresdk

import android.content.Context
import com.klaviyo.coresdk.networking.Clock
import com.klaviyo.coresdk.networking.SystemClock

/**
 * Exception that is thrown when the the Klaviyo API token is missing from the config
 */
class KlaviyoMissingAPIKeyException : Exception("You must declare an API key for the Klaviyo SDK")

/**
 * Exception that is thrown when the application context is missing from the config
 */
class KlaviyoMissingContextException : Exception("You must add your application context to the Klaviyo SDK")

/**
 * Stores all configuration related to the Klaviyo Android SDK.
 */
object KlaviyoConfig {
    internal const val NETWORK_TIMEOUT_DEFAULT: Int = 500
    internal const val NETWORK_FLUSH_INTERVAL_DEFAULT: Int = 60000
    internal const val NETWORK_FLUSH_DEPTH_DEFAULT: Int = 20
    internal const val NETWORK_FLUSH_CHECK_INTERVAL: Int = 2000
    internal const val NETWORK_USE_ANALYTICS_BATCH_QUEUE: Boolean = true
    internal val SYSTEM_CLOCK: Clock = SystemClock

    lateinit var apiKey: String
        private set
    lateinit var applicationContext: Context
        private set
    var networkTimeout = NETWORK_TIMEOUT_DEFAULT
        private set
    var networkFlushInterval = NETWORK_FLUSH_INTERVAL_DEFAULT
        private set
    var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT
        private set
    var networkFlushCheckInterval = NETWORK_FLUSH_CHECK_INTERVAL
        private set
    var networkUseAnalyticsBatchQueue = NETWORK_USE_ANALYTICS_BATCH_QUEUE
        private set
    internal var clock: Clock = SYSTEM_CLOCK
        private set

    /**
     * Nested class to enable the builder pattern for easy declaration of custom configurations
     */
    class Builder {
        private var apiKey: String = ""
        private var applicationContext: Context? = null
        private var networkTimeout: Int = NETWORK_TIMEOUT_DEFAULT
        private var networkFlushInterval: Int = NETWORK_FLUSH_INTERVAL_DEFAULT
        private var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT
        private var networkFlushCheckInterval = NETWORK_FLUSH_CHECK_INTERVAL
        private var networkUseAnalyticsBatchQueue = NETWORK_USE_ANALYTICS_BATCH_QUEUE
        private var clock = SYSTEM_CLOCK

        fun apiKey(apiKey: String) = apply {
            this.apiKey = apiKey
        }

        fun applicationContext(context: Context) = apply {
            this.applicationContext = context
        }

        fun networkTimeout(networkTimeout: Int) = apply {
            if (networkTimeout < 0) {
                // TODO: When Timber is installed, log warning here
            } else {
                this.networkTimeout = networkTimeout
            }
        }

        fun networkFlushInterval(networkFlushInterval: Int) = apply {
            if (networkFlushInterval < 0) {
                // TODO: When Timber is installed, log warning here
            } else {
                this.networkFlushInterval = networkFlushInterval
            }
        }

        fun networkFlushDepth(networkFlushDepth: Int) = apply {
            if (networkFlushDepth <= 0) {
                // TODO: When Timber is installed, log warning here
            } else {
                this.networkFlushDepth = networkFlushDepth
            }
        }

        fun networkFlushCheckInterval(networkFlushCheckInterval: Int) = apply {
            if (networkFlushCheckInterval < 0) {
                // TODO: When Timber is installed, log warning here
            } else {
                this.networkFlushCheckInterval = networkFlushCheckInterval
            }
        }

        fun networkUseAnalyticsBatchQueue(networkUseAnalyticsBatchQueue: Boolean) = apply {
            this.networkUseAnalyticsBatchQueue = networkUseAnalyticsBatchQueue
        }

        internal fun clock(clock: Clock) = apply {
            this.clock = clock
        }

        fun build() {
            if (apiKey.isEmpty()) {
                throw KlaviyoMissingAPIKeyException()
            }
            if (applicationContext == null) {
                throw KlaviyoMissingContextException()
            }

            KlaviyoConfig.apiKey = apiKey
            KlaviyoConfig.applicationContext = applicationContext as Context
            KlaviyoConfig.networkTimeout = networkTimeout
            KlaviyoConfig.networkFlushInterval = networkFlushInterval
            KlaviyoConfig.networkFlushDepth = networkFlushDepth
            KlaviyoConfig.networkFlushCheckInterval = networkFlushCheckInterval
            KlaviyoConfig.networkUseAnalyticsBatchQueue = networkUseAnalyticsBatchQueue
            KlaviyoConfig.clock = clock
        }
    }
}
