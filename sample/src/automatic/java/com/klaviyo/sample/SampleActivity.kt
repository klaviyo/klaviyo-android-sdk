package com.klaviyo.sample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.EventMetric

/**
 * SETUP NOTE: This is the `automatic` flavor's Activity, demonstrating automatic push integration (Option A).
 * By setting com.klaviyo.push.automatic_push_token_forwarding="true" and com.klaviyo.push.automatic_push_open_tracking="true"
 * in the manifest (see src/automatic/AndroidManifest.xml), the SDK takes over both push responsibilities, so
 * there is *zero* push boilerplate here:
 *  - Push token (automatic_push_token_forwarding): auto-registered at Klaviyo.initialize() and on every foreground
 *    (no FirebaseMessaging fetch, no Klaviyo.setPushToken() call). Contrast with the `manual` flavor's onCreate.
 *  - Push opens (automatic_push_open_tracking): the SDK automatically detects when a user taps a push notification
 *    and reports the open event via Klaviyo.handlePush() for you, so there is no handlePush() call in
 *    onNewIntent. Contrast with the `manual` flavor's onNewIntent. Deep links arrive as an Intent, which
 *    this Activity reads in both onCreate and onNewIntent.
 * Displaying notifications still relies on the auto-registered KlaviyoPushService from :sdk:push-fcm.
 * See the main README's "Push Notifications" section (Option A) and sample/README.md.
 */
class SampleActivity : ComponentActivity() {
    // Initialize ViewModel using the by viewModels() delegate
    private val viewModel: SampleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SETUP NOTE (Automatic / Option A): No push-token code here. The SDK auto-registers the token at
        // initialize() and on every foreground. The UI reads it from Klaviyo.getPushToken().

        // Example analytics event to track "Opened App" event on launch
        Klaviyo.createEvent(EventMetric.OPENED_APP)

        // Enable edge-to-edge display for all Android versions for consistency
        WindowCompat.enableEdgeToEdge(window)

        setContent {
            SampleView(
                viewModel = viewModel,
                onRequestNotificationPermission = { askNotificationPermission() },
                onRequestLocationPermission = { askLocationPermission() },
                onRequestBackgroundLocationPermission = { askBackgroundLocationPermission() },
                onShowToast = { message -> showToast(message) }
            )
        }

        // Only on a fresh start: after a configuration change the same intent is re-delivered,
        // which would navigate a second time.
        if (savedInstanceState == null) {
            onNewIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // SETUP NOTE: Handle Universal Tracking Links. The SDK will resolve the destination URL
        // then either invoke your registered deep link handler or send another Intent to your app.
        // (This is unrelated to push open tracking and is required in both integration styles.)
        if (Klaviyo.handleUniversalTrackingLink(intent)) {
            return
        }

        // SETUP NOTE (Automatic / Option A): No Klaviyo.handlePush(intent) here.
        // Because com.klaviyo.push.automatic_push_open_tracking is enabled, the SDK automatically detects
        // notification taps and reports the open for you.

        // SETUP NOTE (Automatic / Option A): A notification tap delivers its deep link here on the
        // Intent, rather than to a registered deep link handler. Handle it in BOTH onCreate (via
        // the call above) and onNewIntent: when the process has been killed but its task is still
        // in recents, Android restores the original intent into onCreate and delivers the new one
        // here. getKlaviyoDeepLink reads the link whether or not a matching intent-filter exists.
        Klaviyo.getKlaviyoDeepLink(intent)?.let { deepLink ->
            showToast("Deep link from intent: $deepLink")
            startActivity(SampleDetailActivity.intent(this, deepLink.toString()))
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Note: you don't need to notify Klaviyo SDK after permission changes
        showToast("Notification permission ${if (isGranted) "granted" else "denied"}")
    }

    /**
     * Notification Permission Handling for Android 13+
     */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Note: It would be typical to show an educational UI here before, omitting in this sample app.
                showToast("Please accept notifications to receive updates from Klaviyo")
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // FCM SDK (and your app) can post notifications.
        }
    }

    private fun askLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Location permission already granted
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Note: It would be typical to show an educational UI here before, omitting in this sample app.
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Directly ask for the permission
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showToast("Location permission granted")
        } else {
            showToast("Location permission not granted")
        }
    }

    private fun askBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Background location permission already granted
            } else if (shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            ) {
                // Show educational UI
                showToast(
                    "Please allow location access 'All the time' to enable geofencing features."
                )
                requestBackgroundLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            } else {
                // Directly ask for the permission
                requestBackgroundLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
        }
    }

    private val requestBackgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showToast("Background location permission granted")
        } else {
            showToast("Background location permission denied")
        }
    }
}
