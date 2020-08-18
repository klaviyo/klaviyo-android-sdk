package com.klaviyo.coresdk

import android.content.Context
import java.lang.Exception

class KlaviyoMissingAPIKeyException: Exception("You must declare an API key for the Klaviyo SDK")

class KlaviyoMissingContextException: Exception("You must add your application context to the Klaviyo SDK")

class KlaviyoConfig {
    internal companion object {
        private const val NETWORK_TIMEOUT_DEFAULT: Int = 500
        private const val NETWORK_FLUSH_INTERVAL_DEFAULT: Int = 60000

        var apiKey: String = ""
        var applicationContext: Context? = null
        var networkTimeout = NETWORK_TIMEOUT_DEFAULT
        var networkFlushInterval = NETWORK_FLUSH_INTERVAL_DEFAULT
    }

    class Builder {
        private var apiKey: String = ""
        private var applicationContext: Context? = null
        private var networkTimeout: Int = NETWORK_TIMEOUT_DEFAULT
        private var networkFlushInterval: Int = NETWORK_FLUSH_INTERVAL_DEFAULT

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

        fun build() {
            if (apiKey.isEmpty()) {
                throw KlaviyoMissingAPIKeyException()
            }
            if (applicationContext == null) {
                throw KlaviyoMissingContextException()
            }

            KlaviyoConfig.apiKey = apiKey
            KlaviyoConfig.applicationContext = applicationContext
            KlaviyoConfig.networkTimeout = networkTimeout
            KlaviyoConfig.networkFlushInterval = networkFlushInterval
        }
    }
}