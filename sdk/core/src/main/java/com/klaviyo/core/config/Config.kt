package com.klaviyo.core.config

import android.content.Context
import com.klaviyo.core.networking.NetworkMonitor

interface Config {
    val baseUrl: String

    val apiKey: String
    val applicationContext: Context
    val userAgent: String

    val debounceInterval: Int

    val networkTimeout: Int
    val networkFlushIntervals: IntArray
    val networkFlushDepth: Int
    val networkMaxRetries: Int

    interface Builder {
        fun apiKey(apiKey: String): Builder
        fun applicationContext(context: Context): Builder
        fun baseUrl(baseUrl: String): Builder
        fun debounceInterval(debounceInterval: Int): Builder
        fun networkTimeout(networkTimeout: Int): Builder
        fun networkFlushInterval(networkFlushInterval: Int, type: NetworkMonitor.NetworkType): Builder
        fun networkFlushDepth(networkFlushDepth: Int): Builder
        fun networkMaxRetries(networkMaxRetries: Int): Builder
        fun build(): Config
    }
}
