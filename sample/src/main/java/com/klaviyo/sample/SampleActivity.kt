package com.klaviyo.sample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.EventMetric

class SampleActivity : ComponentActivity() {
    // Initialize ViewModel using the by viewModels() delegate
    private val viewModel: SampleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Example analytics event to track "Opened App" event on launch
        Klaviyo.createEvent(EventMetric.OPENED_APP)

        // Enable edge-to-edge display for all Android versions
        WindowCompat.enableEdgeToEdge(window)

        setContent {
            SampleView(
                viewModel = viewModel,
                onRequestNotificationPermission = { askNotificationPermission() },
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

        // SETUP NOTE: Track an event when user opens a notification.
        // If the notification is a deep link, the SDK will invoke your registered handler.
        // If not using a deep link handler, you should parse the URI from intent.data below.
        Klaviyo.handlePush(intent)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            // Note: you don't need to notify Klaviyo SDK after permission changes
            viewModel.updatePushToken(it)
        }

        showToast("Notification permission ${if(isGranted) "granted" else "denied"}")
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
}

