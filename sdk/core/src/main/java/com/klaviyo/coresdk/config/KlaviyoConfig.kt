package com.klaviyo.coresdk.config

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.klaviyo.coresdk.BuildConfig

/**
 * Exception that is thrown when the the Klaviyo API token is missing from the config
 */
class KlaviyoMissingAPIKeyException : Exception("You must declare an API key for the Klaviyo SDK")

/**
 * Exception that is thrown when the application context is missing from the config
 */
class KlaviyoMissingContextException : Exception("You must add your application context to the Klaviyo SDK")

/**
 * Exception to throw when a permission is not declared for the application context
 *
 * @param permission
 */
class KlaviyoMissingPermissionException(permission: String) : Exception("You must declare $permission in your manifest to use the Klaviyo SDK")

/**
 * Stores all configuration related to the Klaviyo Android SDK.
 */
object KlaviyoConfig : Config {
    private val SYSTEM_CLOCK: Clock = SystemClock

    private const val NETWORK_TIMEOUT_DEFAULT: Int = 500
    private const val NETWORK_FLUSH_INTERVAL_DEFAULT: Int = 60000
    private const val NETWORK_FLUSH_DEPTH_DEFAULT: Int = 20
    private const val NETWORK_USE_BATCH_QUEUE: Boolean = true

    override val baseUrl: String = BuildConfig.KLAVIYO_SERVER_URL
    override lateinit var apiKey: String
        private set
    override lateinit var applicationContext: Context
        private set
    override var networkTimeout = NETWORK_TIMEOUT_DEFAULT
        private set
    override var networkFlushInterval = NETWORK_FLUSH_INTERVAL_DEFAULT
        private set
    override var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT
        private set
    override var clock: Clock = SYSTEM_CLOCK
        private set

    /**
     * Nested class to enable the builder pattern for easy declaration of custom configurations
     */
    class Builder : Config.Builder {
        private var apiKey: String = ""
        private var applicationContext: Context? = null
        private var networkTimeout: Int = NETWORK_TIMEOUT_DEFAULT
        private var networkFlushInterval: Int = NETWORK_FLUSH_INTERVAL_DEFAULT
        private var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT
        private var clock = SYSTEM_CLOCK

        override fun apiKey(apiKey: String) = apply {
            this.apiKey = apiKey
        }

        override fun applicationContext(context: Context) = apply {
            this.applicationContext = context
        }

        override fun networkTimeout(networkTimeout: Int) = apply {
            if (networkTimeout < 0) {
            } else {
                this.networkTimeout = networkTimeout
            }
        }

        override fun networkFlushInterval(networkFlushInterval: Int) = apply {
            if (networkFlushInterval < 0) {
                // TODO: When Timber is installed, log warning here
            } else {
                this.networkFlushInterval = networkFlushInterval
            }
        }

        override fun networkFlushDepth(networkFlushDepth: Int) = apply {
            if (networkFlushDepth <= 0) {
                // TODO: When Timber is installed, log warning here
            } else {
                this.networkFlushDepth = networkFlushDepth
            }
        }

        override fun build(): Config {
            if (apiKey.isEmpty()) {
                throw KlaviyoMissingAPIKeyException()
            }
            if (applicationContext == null) {
                throw KlaviyoMissingContextException()
            }

            val permissions = applicationContext!!.packageManager.getPackageInfoCompat(
                applicationContext!!.packageName, PackageManager.GET_PERMISSIONS
            ).requestedPermissions ?: arrayOf()

            if (Manifest.permission.ACCESS_NETWORK_STATE !in permissions) {
                throw KlaviyoMissingPermissionException(Manifest.permission.ACCESS_NETWORK_STATE)
            }

            KlaviyoConfig.apiKey = apiKey
            KlaviyoConfig.applicationContext = applicationContext as Context
            KlaviyoConfig.networkTimeout = networkTimeout
            KlaviyoConfig.networkFlushInterval = networkFlushInterval
            KlaviyoConfig.networkFlushDepth = networkFlushDepth
            KlaviyoConfig.clock = clock

            return KlaviyoConfig
        }
    }
}

internal fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
    }
