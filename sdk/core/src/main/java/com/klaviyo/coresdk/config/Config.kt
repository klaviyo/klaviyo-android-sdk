package com.klaviyo.coresdk.config

import android.content.Context

interface Config {
    val baseUrl: String

    val apiKey: String
    val applicationContext: Context

    val networkTimeout: Int
    val networkFlushInterval: Int
    val networkFlushDepth: Int
    val networkFlushCheckInterval: Int
    val networkUseAnalyticsBatchQueue: Boolean
    val clock: Clock

    interface Builder {
        fun apiKey(apiKey: String): Builder
        fun applicationContext(context: Context): Builder
        fun networkTimeout(networkTimeout: Int): Builder
        fun networkFlushInterval(networkFlushInterval: Int): Builder
        fun networkFlushDepth(networkFlushDepth: Int): Builder
        fun networkFlushCheckInterval(networkFlushCheckInterval: Int): Builder
        fun networkUseAnalyticsBatchQueue(networkUseAnalyticsBatchQueue: Boolean): Builder
        fun build(): Config
    }
}
