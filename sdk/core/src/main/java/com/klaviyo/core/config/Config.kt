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
    val networkMaxAttempts: Int
    val networkMaxRetryInterval: Long
    val networkJitterRange: IntRange

    /**
     * Number of consecutive transient failures that trip the network circuit breaker into a
     * dormant state. A value `<= 0` disables the breaker (kill-switch).
     */
    val circuitBreakerFailureThreshold: Int

    /**
     * Initial dormancy interval (milliseconds) the circuit breaker holds the queue once tripped.
     */
    val circuitBreakerBaseOpenInterval: Long

    /**
     * Maximum dormancy interval (milliseconds) for the circuit breaker's exponential backoff.
     */
    val circuitBreakerMaxOpenInterval: Long

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
        fun networkMaxAttempts(networkMaxAttempts: Int): Builder
        fun networkMaxRetryInterval(networkMaxRetryInterval: Long): Builder

        @Deprecated(
            message = "Depth-triggered flushing has been removed. The queue now flushes only on " +
                "the timer interval (see networkFlushInterval) and is internally bounded by a " +
                "size cap. This setter has no effect and will be removed in a future major release.",
            level = DeprecationLevel.WARNING
        )
        fun networkFlushDepth(networkFlushDepth: Int): Builder

        fun build(): Config
    }
}
