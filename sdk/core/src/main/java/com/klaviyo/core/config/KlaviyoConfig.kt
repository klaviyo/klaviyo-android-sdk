package com.klaviyo.core.config

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.PackageManagerCompat
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.KlaviyoException
import com.klaviyo.core.R
import com.klaviyo.core.Registry
import com.klaviyo.core.networking.NetworkMonitor

/**
 * Exception that is thrown when the Klaviyo API token is missing from the config
 */
class MissingAPIKey : KlaviyoException("You must declare an API key for the Klaviyo SDK")

/**
 * Exception that is thrown when the application context is missing from the config
 */
class MissingContext : KlaviyoException("You must add your application context to the Klaviyo SDK")

/**
 * Exception that is thrown when the application context is missing from the config
 */
class LifecycleException : KlaviyoException(
    "Failed to attach lifecycle listeners to the application"
)

/**
 * Exception to throw when a permission is not declared for the application context
 *
 * @param permission
 */
class MissingPermission(permission: String) : KlaviyoException(
    "You must declare $permission in your manifest to use the Klaviyo SDK"
)

/**
 * Stores all configuration related to the Klaviyo Android SDK.
 */
object KlaviyoConfig : Config {
    /**
     * Debounce time for fluent profile setter methods
     *
     * Reasoning: The debounce is only intended to merge chained profile updates into one API call
     */
    private const val DEBOUNCE_INTERVAL: Int = 100

    /**
     * Network request timeout duration
     *
     * Reasoning: Ten seconds accommodates a reasonable network latency
     * On a perfect connection our API calls should take around 1 second
     */
    private const val NETWORK_TIMEOUT_DEFAULT: Int = 10_000

    /**
     * Intervals between flushing network queue, and the basis for retry with exponential backoff
     *
     * Reasoning: A 30-second interval should give radios time to go back to sleep between batches,
     * four retries with a typical backoff pattern would then be 30s, 60s, 3m, 12m.
     */
    private const val NETWORK_FLUSH_INTERVAL_WIFI_DEFAULT = 10_000L
    private const val NETWORK_FLUSH_INTERVAL_CELL_DEFAULT = 30_000L
    private const val NETWORK_FLUSH_INTERVAL_OFFLINE_DEFAULT = 60_000L

    /**
     * How many API requests can be enqueued before flush
     *
     * Reasoning: The goal of depth control is to limit duration that radios are active
     * if a typical request takes 1-3 seconds, this should ideally limit us to 30-90 seconds
     */
    private const val NETWORK_FLUSH_DEPTH_DEFAULT: Int = 25

    /**
     * How many retries to allow an API request before permanent failure
     *
     * Reasoning: Most likely the rate limit should be cleared within 2-3 retries with exp backoff.
     */
    private const val NETWORK_MAX_ATTEMPTS_DEFAULT: Int = 50

    /**
     * Maximum interval between retries for the exponential backoff, in milliseconds (3 minutes)
     *
     * Reasoning: We don't want to wait so long that the user has left the app.
     */
    private const val NETWORK_MAX_RETRY_INTERVAL_DEFAULT: Long = 180_000

    override val isDebugBuild = BuildConfig.DEBUG

    override var baseUrl: String = BuildConfig.KLAVIYO_SERVER_URL
        private set
    override var apiRevision: String = BuildConfig.KLAVIYO_API_REVISION
        private set
    override var baseCdnUrl: String = BuildConfig.KLAVIYO_CDN_URL
        private set
    override var assetSource: String? = BuildConfig.KLAVIYO_ASSET_SOURCE.ifEmpty { null }
        private set
    override lateinit var sdkName: String private set
    override lateinit var sdkVersion: String private set
    override lateinit var apiKey: String private set
    override lateinit var formEnvironment: FormEnvironment
        private set
    override lateinit var applicationContext: Context private set
    override var debounceInterval = DEBOUNCE_INTERVAL
        private set
    override var networkTimeout = NETWORK_TIMEOUT_DEFAULT
        private set
    override var networkFlushIntervals = longArrayOf(
        NETWORK_FLUSH_INTERVAL_WIFI_DEFAULT,
        NETWORK_FLUSH_INTERVAL_CELL_DEFAULT,
        NETWORK_FLUSH_INTERVAL_OFFLINE_DEFAULT
    )
        private set
    override var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT
        private set
    override var networkMaxAttempts = NETWORK_MAX_ATTEMPTS_DEFAULT
        private set
    override var networkMaxRetryInterval = NETWORK_MAX_RETRY_INTERVAL_DEFAULT
        private set
    override val networkJitterRange = 0..10

    override fun getManifestInt(key: String, defaultValue: Int): Int =
        if (!this::applicationContext.isInitialized) {
            defaultValue
        } else {
            applicationContext.getManifestInt(key, defaultValue)
        }

    /**
     * Nested class to enable the builder pattern for easy declaration of custom configurations
     */
    class Builder : Config.Builder {
        private var apiKey: String = ""
        private var applicationContext: Context? = null
        private var baseUrl: String? = null
        private var apiRevision: String? = null
        private var baseCdnUrl: String? = null
        private var assetSource: String? = null
        private var formEnvironment: FormEnvironment = FormEnvironment.IN_APP
        private var sdkName: String? = null
        private var sdkVersion: String? = null
        private var debounceInterval: Int = DEBOUNCE_INTERVAL
        private var networkTimeout: Int = NETWORK_TIMEOUT_DEFAULT
        private var networkFlushIntervals = longArrayOf(
            NETWORK_FLUSH_INTERVAL_WIFI_DEFAULT,
            NETWORK_FLUSH_INTERVAL_CELL_DEFAULT,
            NETWORK_FLUSH_INTERVAL_OFFLINE_DEFAULT
        )
        private var networkFlushDepth = NETWORK_FLUSH_DEPTH_DEFAULT
        private var networkMaxAttempts = NETWORK_MAX_ATTEMPTS_DEFAULT
        private var networkMaxRetryInterval = NETWORK_MAX_RETRY_INTERVAL_DEFAULT

        private val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
        )

        override fun apiKey(apiKey: String) = apply {
            this.apiKey = apiKey
        }

        override fun applicationContext(context: Context) = apply {
            this.applicationContext = context
        }

        override fun baseUrl(baseUrl: String): Config.Builder = apply {
            this.baseUrl = baseUrl
        }

        override fun apiRevision(apiRevision: String): Config.Builder = apply {
            this.apiRevision = apiRevision
        }

        override fun baseCdnUrl(baseCdnUrl: String): Config.Builder = apply {
            this.baseCdnUrl = baseCdnUrl
        }

        override fun assetSource(assetSource: String?): Config.Builder = apply {
            this.assetSource = assetSource
        }

        override fun debounceInterval(debounceInterval: Int) = apply {
            if (debounceInterval >= 0) {
                this.debounceInterval = debounceInterval
            } else {
                Registry.log.error(
                    "${KlaviyoConfig::debounceInterval.name} must be greater or equal to 0"
                )
            }
        }

        override fun formEnvironment(formEnvironment: FormEnvironment) = apply {
            this.formEnvironment = formEnvironment
        }

        override fun networkTimeout(networkTimeout: Int) = apply {
            if (networkTimeout >= 0) {
                this.networkTimeout = networkTimeout
            } else {
                Registry.log.error(
                    "${KlaviyoConfig::networkTimeout.name} must be greater or equal to 0"
                )
            }
        }

        override fun networkFlushInterval(
            networkFlushInterval: Long,
            type: NetworkMonitor.NetworkType
        ) = apply {
            if (networkFlushInterval >= 0) {
                this.networkFlushIntervals[type.position] = networkFlushInterval
            } else {
                Registry.log.error(
                    "${KlaviyoConfig::networkFlushIntervals.name} must be greater or equal to 0"
                )
            }
        }

        override fun networkFlushDepth(networkFlushDepth: Int) = apply {
            if (networkFlushDepth > 0) {
                this.networkFlushDepth = networkFlushDepth
            } else {
                Registry.log.error(
                    "${KlaviyoConfig::networkFlushDepth.name} must be greater than 0"
                )
            }
        }

        override fun networkMaxAttempts(networkMaxAttempts: Int) = apply {
            if (networkMaxAttempts >= 0) {
                this.networkMaxAttempts = networkMaxAttempts
            } else {
                Registry.log.error(
                    "${KlaviyoConfig::networkMaxAttempts.name} must be greater or equal to 0"
                )
            }
        }

        override fun networkMaxRetryInterval(networkMaxRetryInterval: Long) = apply {
            if (networkMaxRetryInterval >= 0) {
                this.networkMaxRetryInterval = networkMaxRetryInterval
            } else {
                Registry.log.error(
                    "${KlaviyoConfig::networkMaxRetryInterval.name} must be greater or equal to 0"
                )
            }
        }

        override fun build(): Config {
            if (apiKey.isEmpty()) {
                throw MissingAPIKey()
            }

            val context = applicationContext ?: throw MissingContext()
            val packageInfo = context.packageManager.getPackageInfoCompat(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            packageInfo.assertRequiredPermissions(requiredPermissions)

            baseUrl?.let { KlaviyoConfig.baseUrl = it }
            apiRevision?.let { KlaviyoConfig.apiRevision = it }
            baseCdnUrl?.let { KlaviyoConfig.baseCdnUrl = it }
            assetSource?.let { KlaviyoConfig.assetSource = it }
            formEnvironment.let { KlaviyoConfig.formEnvironment = it }

            KlaviyoConfig.sdkName = this.sdkName ?: context.resources.getString(
                R.string.klaviyo_sdk_name_override
            )
            KlaviyoConfig.sdkVersion = this.sdkVersion ?: context.resources.getString(
                R.string.klaviyo_sdk_version_override
            )

            KlaviyoConfig.apiKey = apiKey
            KlaviyoConfig.applicationContext = context

            KlaviyoConfig.debounceInterval = debounceInterval
            KlaviyoConfig.networkTimeout = networkTimeout
            KlaviyoConfig.networkFlushIntervals = networkFlushIntervals
            KlaviyoConfig.networkFlushDepth = networkFlushDepth
            KlaviyoConfig.networkMaxAttempts = networkMaxAttempts
            KlaviyoConfig.networkMaxRetryInterval = networkMaxRetryInterval

            return KlaviyoConfig
        }
    }
}

internal fun PackageInfo.assertRequiredPermissions(requiredPermissions: Array<String>) {
    val permissions = requestedPermissions?.toSet() ?: emptySet()
    requiredPermissions.firstOrNull { it !in permissions }?.let { throw MissingPermission(it) }
}

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, flags)
    }

/**
 * Extension method since there is no support for this in yet in [PackageManagerCompat]
 *
 * NOTE: There is no other option than the deprecated method below Tiramisu
 *
 * @param pkgName
 * @param flags
 * @return [ApplicationInfo]
 */
@Suppress("DEPRECATION")
fun PackageManager.getApplicationInfoCompat(
    pkgName: String,
    flags: Int = 0
): ApplicationInfo? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(
            pkgName,
            PackageManager.ApplicationInfoFlags.of(flags.toLong())
        )
    } else {
        getApplicationInfo(pkgName, flags)
    }
} catch (e: PackageManager.NameNotFoundException) {
    Registry.log.error("Application info unavailable", e)
    null
}

/**
 * Extension method to get an integer value from the manifest metadata
 */
fun Context.getManifestInt(key: String, defaultValue: Int): Int {
    val pkgName = packageName
    val pkgManager = packageManager
    val appInfo = pkgManager.getApplicationInfoCompat(pkgName, PackageManager.GET_META_DATA)
    val manifestMetadata = appInfo?.metaData ?: Bundle.EMPTY
    return manifestMetadata.getInt(key, defaultValue)
}
