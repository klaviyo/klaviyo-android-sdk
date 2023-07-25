package com.klaviyo.analytics

import android.Manifest
import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.config.getPackageInfoCompat
import com.klaviyo.core.model.fetchOrCreate
import java.util.UUID

internal object DeviceProperties {

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

    val sdkVersion: String by lazy {
        BuildConfig.VERSION
    }

    val sdkName: String by lazy {
        "android"
    }

    val backgroundData: Boolean by lazy {
        activityManager.isBackgroundRestrictedCompat()
    }

    val notificationPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
            Registry.config.applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

    val applicationId: String by lazy {
        Registry.config.applicationContext.packageName
    }

    val applicationLabel: String by lazy {
        Registry.config.applicationContext.packageManager.getApplicationLabel(
            Registry.config.applicationContext.applicationInfo
        ).toString()
    }

    val userAgent: String by lazy {
        "$applicationLabel/$appVersion ($applicationId; build:$appVersionCode; $platform $osVersion) klaviyo-android/$sdkVersion"
    }

    private val packageInfo: PackageInfo by lazy {
        Registry.config.applicationContext.packageManager.getPackageInfoCompat(applicationId)
    }

    private val activityManager: ActivityManager by lazy {
        Registry.config.applicationContext.getSystemService(ActivityManager::class.java)
    }

    fun buildEventMetaData(): Map<String, String?> = mapOf(
        "Device ID" to deviceId,
        "Device Manufacturer" to manufacturer,
        "Device Model" to model,
        "OS Name" to platform,
        "OS Version" to osVersion,
        "SDK Name" to sdkName,
        "SDK Version" to sdkVersion,
        "App Name" to applicationLabel,
        "App ID" to applicationId,
        "App Version" to appVersion,
        "App Build" to appVersionCode,
        "Push Token" to Klaviyo.getPushToken()
    )

    fun buildMetaData(): Map<String, String?> = mapOf(
        "device_id" to deviceId,
        "manufacturer" to manufacturer,
        "device_model" to model,
        "os_name" to platform,
        "os_version" to osVersion,
        "klaviyo_sdk" to sdkName,
        "sdk_version" to sdkVersion,
        "app_name" to applicationLabel,
        "app_id" to applicationId,
        "app_version" to appVersion,
        "app_build" to appVersionCode,
        "environment" to getEnvironment()
    )

    private fun getEnvironment(): String =
        if (Registry.config.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "debug"
        } else {
            "release"
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
