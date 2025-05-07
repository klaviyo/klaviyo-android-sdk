package com.klaviyo.core

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.klaviyo.core.config.KlaviyoConfig
import com.klaviyo.core.config.getPackageInfoCompat
import com.klaviyo.core.model.fetchOrCreate
import java.util.UUID

object DeviceProperties {

    private const val DEVICE_ID_KEY = "device_id"

    /**
     * UUID for this Device + SDK installation
     * should only be generated one time, stored for the life of the app installation
     */
    val deviceId: String by lazy {
        Registry.dataStore.fetchOrCreate(DEVICE_ID_KEY) { UUID.randomUUID().toString() }
    }

    val manufacturer: String by lazy {
        Build.BRAND
    }

    val model: String by lazy {
        Build.MODEL
    }

    val platform: String by lazy {
        "Android"
    }

    val osVersion: String by lazy {
        Build.VERSION.SDK_INT.toString()
    }

    val appVersion: String by lazy {
        packageInfo.versionName
    }

    val appVersionCode: String by lazy {
        packageInfo.getVersionCodeCompat().toString()
    }

    val sdkVersion: String
        get() = KlaviyoConfig.sdkVersion

    val sdkName: String
        get() = KlaviyoConfig.sdkName

    val backgroundDataEnabled: Boolean by lazy {
        !activityManager.isBackgroundRestrictedCompat()
    }

    val notificationPermissionGranted: Boolean
        get() = NotificationManagerCompat.from(Registry.config.applicationContext)
            .areNotificationsEnabled()

    val applicationId: String by lazy {
        Registry.config.applicationContext.packageName
    }

    val applicationLabel: String by lazy {
        Registry.config.applicationContext.packageManager.getApplicationLabel(
            Registry.config.applicationContext.applicationInfo
        ).toString()
    }

    val pluginSdk: String? by lazy {
        Registry.config.applicationContext.getString(R.string.klaviyo_sdk_plugin_name_override)
            .ifBlank { null }
    }

    val pluginSdkVersion: String? by lazy {
        Registry.config.applicationContext.getString(R.string.klaviyo_sdk_plugin_version_override)
            .ifBlank { null }
    }

    val userAgent: String
        get() {
            val pluginString = pluginSdk?.let {
                "($it/${pluginSdkVersion ?: "UNKNOWN"})"
            }
            val sdkAgent = "klaviyo-${sdkName.replace("_", "-")}"
            return "$applicationLabel/$appVersion ($applicationId; build:$appVersionCode; $platform $osVersion)" +
                " $sdkAgent/$sdkVersion${pluginString?.let { " $it" } ?: ""}"
        }

    val environment: String by lazy {
        if (Registry.config.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "debug"
        } else {
            "release"
        }
    }

    private val packageInfo: PackageInfo by lazy {
        Registry.config.applicationContext.packageManager.getPackageInfoCompat(applicationId)
    }

    private val activityManager: ActivityManager by lazy {
        Registry.config.applicationContext.getSystemService(ActivityManager::class.java)
    }
}

internal fun PackageInfo.getVersionCodeCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        (longVersionCode and 0xffffffffL).toInt()
    } else {
        @Suppress("DEPRECATION")
        versionCode
    }

internal fun ActivityManager.isBackgroundRestrictedCompat(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        isBackgroundRestricted
    } else {
        false
    }
