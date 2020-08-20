package com.klaviyo.coresdk

import android.content.Context
import java.lang.Exception

class KlaviyoMissingAPIKeyException: Exception("You must declare an API key for the Klaviyo SDK")

class KlaviyoMissingContextException: Exception("You must add your application context to the Klaviyo SDK")

object KlaviyoConfig {
    private const val NETWORK_TIMEOUT_DEFAULT: Int = 500
    private const val NETWORK_FLUSH_INTERVAL_DEFAULT: Int = 60000
    private const val NETWORK_FLUSH_DEPTH_DEFAULT: Int = 20

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

    class Builder {
        private var apiKey: String = ""
        private var applicationContext: Context? = null
        private var networkTimeout: Int = NETWORK_TIMEOUT_DEFAULT
        private var networkFlushInterval: Int = NETWORK_FLUSH_INTERVAL_DEFAULT
        private var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT

        fun apiKey(apiKey: String) = apply {
            this.apiKey = apiKey
        }

        fun applicationContext(context: Context) = apply {
            this.applicationContext = context
        }

        fun networkTimeout(networkTimeout: Int)  = apply {
            if (networkTimeout < 0) {
                // TODO: When Timber is installed, log warning here
            } else {
                this.networkTimeout = networkTimeout
            }
        }

        fun networkFlushInterval(networkFlushInterval: Int)  = apply {
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
        }
    }
}
