package com.klaviyo.analytics

import android.content.pm.PackageInfo
import android.os.Build
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.config.getPackageInfoCompat

internal object DeviceProperties {

    val manufacturer: String by lazy {
        Build.BRAND
    }

    val deviceModel: String by lazy {
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
}

internal fun PackageInfo.getVersionCodeCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        (longVersionCode and 0xffffffffL).toInt()
    } else {
        @Suppress("DEPRECATION")
        versionCode
    }
