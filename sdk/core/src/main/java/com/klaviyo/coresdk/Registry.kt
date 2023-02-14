package com.klaviyo.coresdk

import com.klaviyo.coresdk.config.Clock
import com.klaviyo.coresdk.config.Config
import com.klaviyo.coresdk.config.KlaviyoConfig
import com.klaviyo.coresdk.config.SystemClock
import com.klaviyo.coresdk.lifecycle.KlaviyoLifecycleMonitor
import com.klaviyo.coresdk.lifecycle.LifecycleMonitor
import com.klaviyo.coresdk.model.DataStore
import com.klaviyo.coresdk.model.SharedPreferencesDataStore
import com.klaviyo.coresdk.networking.ApiClient
import com.klaviyo.coresdk.networking.KlaviyoApiClient
import com.klaviyo.coresdk.networking.KlaviyoNetworkMonitor
import com.klaviyo.coresdk.networking.NetworkMonitor

/**
 * Services registry for decoupling SDK components
 * Acts as a very basic IoC container for dependencies
 */
object Registry {
    internal val configBuilder: Config.Builder get() = KlaviyoConfig.Builder()

    internal lateinit var config: Config

    internal var clock: Clock = SystemClock

    internal val lifecycleMonitor: LifecycleMonitor get() = KlaviyoLifecycleMonitor

    internal val networkMonitor: NetworkMonitor get() = KlaviyoNetworkMonitor

    val apiClient: ApiClient get() = KlaviyoApiClient

    val dataStore: DataStore get() = SharedPreferencesDataStore
}
