package com.klaviyo.coresdk

data class KlaviyoConfig (
        val apiKey: String,
        val networkTimeout: Int,
        val networkFlushInterval: Int
    ) {
    class Builder {
        private val NETWORK_TIMEOUT_DEFAULT: Int = 500
        private val NETWORK_FLUSH_INTERVAL_DEFAULT: Int = 60000

        private lateinit var apiKey: String
        private var networkTimeout: Int = NETWORK_TIMEOUT_DEFAULT
        private var networkFlushInterval: Int = NETWORK_FLUSH_INTERVAL_DEFAULT

        fun apiKey(apiKey: String) = apply {
            this.apiKey = apiKey
        }

        fun networkTimeout(networkTimeout: Int)  = apply {
            this.networkTimeout = networkTimeout
        }

        fun networkFlushInterval(networkFlushInterval: Int)  = apply {
            this.networkFlushInterval = networkFlushInterval
        }

        fun build() = KlaviyoConfig(
                apiKey,
                networkTimeout,
                networkFlushInterval
        )
    }
}