package com.klaviyo.sdktestapp.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.sdktestapp.services.Clipboard
import com.klaviyo.sdktestapp.services.PushService

@SuppressLint("InlinedApi") // Safe to use the keyword. ActivityCompat handles API level differences
class PushSettingsViewModel(
    private val context: Context,
    private val pushNotificationContract: ActivityResultLauncher<String>,
) {

    data class ViewState(
        val isNotificationPermitted: Boolean,
        val pushToken: String,
    )

    var viewState by mutableStateOf(
        ViewState(
            isNotificationPermitted = false,
            pushToken = "",
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

    /**
     * Check if we have user's permission to post notifications
     * NOTE: Not the same as whether we have a push token
     *
     * @return
     */
    private fun isNotificationPermissionGranted(): Boolean = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    private fun getPushToken(): String {
        return Klaviyo.getPushToken() ?: ""
    }

    fun refreshViewModel() {
        viewState = ViewState(
            isNotificationPermitted = isNotificationPermissionGranted(),
            pushToken = getPushToken(),
        )
    }

    fun setSdkPushToken() = PushService.setSdkPushToken()

    fun sendLocalNotification() = PushService.createLocalNotification(context)

    fun imGonnaWreckIt() {
        // Force a crash for crashlytics
        throw RuntimeException("Test Crash")
    }

    fun requestPushNotifications() {
        // https://klaviyo.atlassian.net/wiki/spaces/EN/pages/3675848705/Android+Notification+Permission
        when {
            isNotificationPermissionGranted() -> {
                // GRANTED - We have notification permission
                // Safeguard: this method shouldn't be called from the UI if push is already enabled
                return
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                // Only reachable on API level 33+
                // when permission was denied before, but we are still allowed
                // to display an educational dialog and request permission again.
                // Request the permission, which invokes a callback method
                AlertDialog.Builder(context)
                    .setTitle("Notifications Permission")
                    .setMessage("Permission must be granted in order to receive push notifications in the system tray.")
                    .setCancelable(true)
                    .setPositiveButton("Grant") { _, _ ->
                        // You can directly ask for the permission.
                        // The registered ActivityResultCallback gets the result of this request.
                        pushNotificationContract.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton("Cancel") { _, _ -> }
                    .show()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Request the permission, which invokes a callback method
                pushNotificationContract.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                // Only reachable below API Level 33
                // DENIED - Notifications were turned off by the user in system settings
                alertPermissionDenied()
            }
        }
    }

    fun alertPermissionDenied() {
        AlertDialog.Builder(context)
            .setTitle("Notifications Disabled")
            .setMessage("Permission is denied and can only be changed from notification settings.")
            .setCancelable(true)
            .setPositiveButton("Settings...") { _, _ -> openSettings() }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    fun openSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        context.startActivity(intent)
    }

    fun copyPushToken() {
        Clipboard(context).logAndCopy("Push Token", viewState.pushToken)
    }
}
