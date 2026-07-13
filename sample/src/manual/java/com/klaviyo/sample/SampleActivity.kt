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
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.Klaviyo.isKlaviyoNotificationIntent
import com.klaviyo.analytics.model.EventMetric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SETUP NOTE: This is the `manual` flavor's Activity, demonstrating manual push integration (Option B):
 * the app fetches the push token and forwards it to Klaviyo, and calls [Klaviyo.handlePush] itself on
 * notification taps. Compare with the `automatic` flavor's copy under `src/automatic`, where both of those
 * responsibilities are handled by the SDK and this boilerplate simply disappears. See the main README's
 * "Push Notifications" section (Option A vs Option B) and sample/README.md.
 */
class SampleActivity : ComponentActivity() {
    // Initialize ViewModel using the by viewModels() delegate
    private val viewModel: SampleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SETUP NOTE (Manual / Option B): Fetch the current push token and register it with Klaviyo.
        // The `automatic` flavor omits this entirely — the SDK auto-registers the token at initialize()
        // and on every foreground once com.klaviyo.push.automatic_token_forwarding is enabled.
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            // Dispatch to main for the UI update
            lifecycleScope.launch(Dispatchers.Main) {
                viewModel.updatePushToken(token)
            }
        }

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

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // SETUP NOTE: Handle Universal Tracking Links. The SDK will resolve the destination URL
        // then either invoke your registered deep link handler or send another Intent to your app.
        if (Klaviyo.handleUniversalTrackingLink(intent)) {
            return
        }

        // SETUP NOTE (Manual / Option B): Track an event when the user opens a notification.
        // If the notification is a deep link, the SDK will invoke your registered handler.
        // If not using a deep link handler, you should parse the URI from intent.data below.
        // The `automatic` flavor omits this — KlaviyoTrampolineActivity calls handlePush() for you.
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
