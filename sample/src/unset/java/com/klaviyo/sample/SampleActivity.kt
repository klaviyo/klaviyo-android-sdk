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
import com.klaviyo.analytics.Klaviyo.isKlaviyoNotificationIntent
import com.klaviyo.analytics.model.EventMetric

/**
 * SETUP NOTE: This is the `unset` flavor's Activity, demonstrating the behavior when neither
 * com.klaviyo.push.automatic_push_token_forwarding nor com.klaviyo.push.automatic_push_open_tracking
 * is declared in the manifest (see src/unset/AndroidManifest.xml). This is what an integration that
 * predates those flags gets:
 *  - Push token: this app registers no token itself, and the SDK does not fetch one at
 *    Klaviyo.initialize() or on foreground. Tokens reach Klaviyo only when the push provider delivers
 *    one to KlaviyoPushService.onNewToken. Contrast with `automatic` (proactive fetch) and `manual`
 *    (this app fetches and forwards).
 *  - Push opens: no automatic open tracking, so this Activity calls Klaviyo.handlePush() itself, the
 *    same as the `manual` flavor. Deep links arrive via the handler registered in SampleApplication.
 * See sample/README.md.
 */
class SampleActivity : ComponentActivity() {
    // Initialize ViewModel using the by viewModels() delegate
    private val viewModel: SampleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SETUP NOTE (unset): No push-token code here, and no proactive fetch by the SDK either. The
        // UI reads whatever token the SDK has via Klaviyo.getPushToken(), which stays empty until the
        // push provider delivers a token to KlaviyoPushService.onNewToken.

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
        if (Klaviyo.handleUniversalTrackingLink(intent)) {
            return
        }

        // SETUP NOTE (unset): Track an event when the user opens a notification. Automatic open
        // tracking is opt-in, so with the flag absent this call is required.
        if (intent.isKlaviyoNotificationIntent) {
            Klaviyo.handlePush(intent)
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
