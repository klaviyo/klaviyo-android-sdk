package com.klaviyo.coresdk

import android.content.Context
import java.lang.Exception

class KlaviyoMissingAPIKeyException: Exception("You must declare an API key for the Klaviyo SDK")

class KlaviyoMissingContextException: Exception("You must add your application context to the Klaviyo SDK")

class KlaviyoConfig private constructor(
        apiKey: String,
        applicationContext: Context?,
        networkTimeout: Int,
        networkFlushInterval: Int) {
    companion object {
        private const val NETWORK_TIMEOUT_DEFAULT: Int = 500
        private const val NETWORK_FLUSH_INTERVAL_DEFAULT: Int = 60000
    }

    val apiKey: String
    val applicationContext: Context?
    val networkTimeout: Int
    val networkFlushInterval: Int

    init {
        if (apiKey.isNullOrEmpty()) {
            throw KlaviyoMissingAPIKeyException()
        }
        if (applicationContext == null) {
            throw KlaviyoMissingContextException()
        }

        this.apiKey = apiKey
        this.applicationContext = applicationContext
        this.networkTimeout = networkTimeout
        this.networkFlushInterval = networkFlushInterval
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

        fun build() = KlaviyoConfig(
                apiKey,
                applicationContext,
                networkTimeout,
                networkFlushInterval
        )
    }
}