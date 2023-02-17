package com.klaviyo.core.config

import android.content.Context

interface Config {
    val baseUrl: String

    val apiKey: String
    val applicationContext: Context

    val debounceInterval: Int

    val networkTimeout: Int
    val networkFlushInterval: Int
    val networkFlushDepth: Int
    val networkMaxRetries: Int

    interface Builder {
        fun apiKey(apiKey: String): Builder
        fun applicationContext(context: Context): Builder
        fun debounceInterval(debounceInterval: Int): Builder
        fun networkTimeout(networkTimeout: Int): Builder
        fun networkFlushInterval(networkFlushInterval: Int): Builder
        fun networkFlushDepth(networkFlushDepth: Int): Builder
        fun networkMaxRetries(networkMaxRetries: Int): Builder
        fun build(): Config
    }
}
