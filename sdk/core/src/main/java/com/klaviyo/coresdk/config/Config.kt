package com.klaviyo.coresdk.config

import android.content.Context

interface Config {
    val baseUrl: String

    val apiKey: String
    val applicationContext: Context

    val debounceInterval: Int

    val networkTimeout: Int
    // TODO: Could use some better data structure for keying the different network interval settings
    //  But since this is publicly exposed, should I just stick to basic integers for each instead?
    val networkFlushIntervals: IntArray
//    val networkFlushIntervalCell: Int
//    val networkFlushIntervalWifi: Int
//    val networkFlushIntervalOffline: Int
    val networkFlushDepth: Int
    val networkMaxRetries: Int

    interface Builder {
        fun apiKey(apiKey: String): Builder
        fun applicationContext(context: Context): Builder
        fun debounceInterval(debounceInterval: Int): Builder
        fun networkTimeout(networkTimeout: Int): Builder
        fun networkFlushIntervalCell(networkFlushIntervalCell: Int): Builder
        fun networkFlushIntervalWifi(networkFlushIntervalWifi: Int): Builder
        fun networkFlushIntervalOffline(networkFlushIntervalOffline: Int): Builder
        fun networkFlushDepth(networkFlushDepth: Int): Builder
        fun networkMaxRetries(networkMaxRetries: Int): Builder
        fun build(): Config
    }
}
