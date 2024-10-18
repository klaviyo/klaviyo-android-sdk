package com.klaviyo.sdktestapp.viewmodel

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.sdktestapp.services.Clipboard
import com.klaviyo.sdktestapp.services.ConfigService
import com.klaviyo.sdktestapp.services.PushService

interface ISettingsViewModel {
    val viewState: SettingsViewModel.ViewState
    fun setSdkPushToken()
    fun expirePushToken()
    fun sendLocalNotification()
    fun setBaseUrl()
    fun setApiRevision()
    fun imGonnaWreckIt()
    fun requestPushNotifications()
    fun alertPermissionDenied(): AlertDialog
    fun openSettings()
    fun copyPushToken()
}

class SettingsViewModel(
    private val context: Context,
    private val pushNotificationContract: ActivityResultLauncher<String>
) : ISettingsViewModel {
    data class ViewState(
        val isNotificationPermitted: Boolean,
        val pushToken: String,
        val baseUrl: MutableState<String>,
        val apiRevision: MutableState<String>
    )

    override var viewState by mutableStateOf(
        ViewState(
            // warning: we cannot access notification manager until after app launch is complete
            isNotificationPermitted = false,
            pushToken = "",
            baseUrl = mutableStateOf(Registry.config.baseUrl),
            apiRevision = mutableStateOf(Registry.config.apiRevision)
        )
    )
        private set

    init {
        Registry.dataStore.onStoreChange { key, _ ->
            // Observe SDK data store for changes to the push token key
            if (key == "push_token") refreshViewModel()
        }

        Registry.lifecycleMonitor.onActivityEvent {
            // On application resume, re-check permission state
            if (it is ActivityEvent.Resumed) refreshViewModel()
        }
    }

    private fun getPushToken(): String {
        return Klaviyo.getPushToken() ?: ""
    }

    fun refreshViewModel() {
        viewState = ViewState(
            isNotificationPermitted = context.notificationManager.areNotificationsEnabled(),
            pushToken = getPushToken(),
            baseUrl = mutableStateOf(Registry.config.baseUrl),
            apiRevision = mutableStateOf(Registry.config.apiRevision)
        )
    }

    override fun setSdkPushToken() = PushService.setSdkPushToken()

    override fun expirePushToken() {
        FirebaseMessaging.getInstance().deleteToken()
            .addOnSuccessListener {
                setSdkPushToken()
            }
    }

    override fun sendLocalNotification() = PushService.createLocalNotification(context)

    override fun setBaseUrl() {
        Registry.get<ConfigService>().baseUrl = viewState.baseUrl.value
    }

    override fun setApiRevision() {
        Registry.get<ConfigService>().apiRevision = viewState.apiRevision.value
    }

    override fun imGonnaWreckIt() {
        // Force a crash for crashlytics
        throw RuntimeException("Test Crash")
    }

    /**
     * Note: this method shouldn't be called from the UI if push is already enabled,
     * but we'll still use the best practice here for our when statement for completeness
     */
    override fun requestPushNotifications() {
        // https://klaviyo.atlassian.net/wiki/spaces/EN/pages/3675848705/Android+Notification+Permission
        when {
            context.notificationManager.areNotificationsEnabled() -> {
                // GRANTED - We have already notification permission
                return
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                // Reachable on API level >= 33
                // If a permission prompt was previously denied, display an educational UI and request permission again
                requestPermissionWithRationale()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Reachable on API Level >= 33
                // We can request the permission
                pushNotificationContract.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                // Reachable on API Level < 33
                // DENIED - Notifications were turned off by the user in system settings
                alertPermissionDenied()
            }
        }
    }

    private fun requestPermissionWithRationale() = AlertDialog.Builder(context)
        .setTitle("Notifications Permission")
        .setMessage(
            "Permission must be granted in order to receive push notifications in the system tray."
        )
        .setCancelable(true)
        .setPositiveButton("Grant") { _, _ ->
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            pushNotificationContract.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        .setNegativeButton("Cancel") { _, _ -> }
        .show()

    override fun alertPermissionDenied(): AlertDialog = AlertDialog.Builder(context)
        .setTitle("Notifications Disabled")
        .setMessage("Permission is denied and can only be changed from notification settings.")
        .setCancelable(true)
        .setPositiveButton("Settings...") { _, _ -> openSettings() }
        .setNegativeButton("Cancel") { _, _ -> }
        .show()

    override fun openSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        context.startActivity(intent)
    }

    override fun copyPushToken() {
        Clipboard(context).logAndCopy("Push Token", viewState.pushToken)
    }

    private val Context.notificationManager: NotificationManagerCompat get() =
        NotificationManagerCompat.from(this)
}
