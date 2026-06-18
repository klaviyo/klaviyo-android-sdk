package com.klaviyo.core.config

import android.content.Context
import com.klaviyo.core.networking.NetworkMonitor

interface Config {
    val isDebugBuild: Boolean
    val baseUrl: String
    val apiRevision: String
    val baseCdnUrl: String
    val assetSource: String?
    val sdkName: String
    val sdkVersion: String
    val formEnvironment: FormEnvironment

    val apiKey: String
    val applicationContext: Context

    val debounceInterval: Int

    val networkTimeout: Int
    val uxNetworkTimeout: Int
    val networkFlushIntervals: LongArray
    val networkFlushDepth: Int
    val networkMaxAttempts: Int
    val networkMaxRetryInterval: Long
    val networkJitterRange: IntRange

    fun getManifestInt(key: String, defaultValue: Int): Int

    interface Builder {
        fun apiKey(apiKey: String): Builder
        fun applicationContext(context: Context): Builder
        fun baseUrl(baseUrl: String): Builder
        fun baseCdnUrl(baseCdnUrl: String): Builder
        fun assetSource(assetSource: String?): Builder
        fun apiRevision(apiRevision: String): Builder
        fun debounceInterval(debounceInterval: Int): Builder
        fun formEnvironment(formEnvironment: FormEnvironment): Builder
        fun networkTimeout(networkTimeout: Int): Builder
        fun uxNetworkTimeout(uxNetworkTimeout: Int): Builder
        fun networkFlushInterval(networkFlushInterval: Long, type: NetworkMonitor.NetworkType): Builder
        fun networkFlushDepth(networkFlushDepth: Int): Builder
        fun networkMaxAttempts(networkMaxAttempts: Int): Builder
        fun networkMaxRetryInterval(networkMaxRetryInterval: Long): Builder
        fun build(): Config
    }
}
