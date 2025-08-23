package com.klaviyo.sample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.forms.registerForInAppForms
import com.klaviyo.forms.unregisterFromInAppForms
import com.klaviyo.sample.ui.theme.KlaviyoAndroidSdkTheme

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for all Android versions
        WindowCompat.enableEdgeToEdge(window)

        setContent {
            // State management within the Composable
            var externalId by remember { mutableStateOf(Klaviyo.getExternalId() ?: "") }
            var email by remember { mutableStateOf(Klaviyo.getEmail() ?: "") }
            var phoneNumber by remember { mutableStateOf(Klaviyo.getPhoneNumber() ?: "") }
            var pushToken by remember { mutableStateOf(Klaviyo.getPushToken() ?: "") }
            var notificationPermission by remember { mutableStateOf(false) }
            
            // Update notification permission when resuming
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationPermission = NotificationManagerCompat.from(this@SampleActivity).areNotificationsEnabled()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            
            KlaviyoAndroidSdkTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SampleView(
                        externalId = externalId,
                        email = email,
                        phoneNumber = phoneNumber,
                        pushToken = pushToken,
                        hasNotificationPermission = notificationPermission,
                        onExternalIdChange = { externalId = it },
                        onEmailChange = { email = it },
                        onPhoneNumberChange = { phoneNumber = it },
                        setProfile = { setProfile(externalId, email, phoneNumber) },
                        resetProfile = {
                            externalId = ""
                            email = ""
                            phoneNumber = ""
                            resetProfile()
                        },
                        createTestEvent = ::createTestEvent,
                        createViewedProductEvent = ::createViewedProductEvent,
                        registerForInAppForms = ::registerForInAppForms,
                        unregisterFromInAppForms = ::unregisterFromInAppForms,
                        requestPermission = { 
                            askNotificationPermission()
                            notificationPermission = NotificationManagerCompat.from(this@SampleActivity).areNotificationsEnabled()
                        },
                    )
                }
            }
        }

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // Parse a deep link from an opened notification or In-App Form
        intent?.data?.let {
            Toast.makeText(
                this.applicationContext,
                "New intent with URI: ${intent.data}",
                Toast.LENGTH_LONG
            ).show()
        }

        //Tracks when a system tray notification is opened
        Klaviyo.handlePush(intent)
    }

    override fun onResume() {
        super.onResume()
        // Note: notification permission state is now managed in Compose
    }

    private fun setProfile(externalId: String, email: String, phoneNumber: String) {
        Klaviyo
            .setExternalId(externalId)
            .setEmail(email)
            .setPhoneNumber(phoneNumber)

        Toast.makeText(this, "Profile set", Toast.LENGTH_SHORT).show()
    }

    private fun resetProfile() {
        Klaviyo.resetProfile()
        Toast.makeText(this, "Profile reset", Toast.LENGTH_SHORT).show()
    }

    private fun createTestEvent() {
        val event = Event(EventMetric.CUSTOM("Test Event"))
            .setProperty(EventKey.CUSTOM("System Time"), System.currentTimeMillis() / 1000L)

        Klaviyo.createEvent(event)

        Toast.makeText(this, "Created ${event.metric.name} event", Toast.LENGTH_SHORT).show()
    }

    private fun createViewedProductEvent() {
        val event = Event(EventMetric.VIEWED_PRODUCT)
            .setProperty(EventKey.VALUE, 100)
            .setProperty(EventKey.CUSTOM("Product"), "Lily Pad")

        Klaviyo.createEvent(event)

        Toast.makeText(this, "Created ${event.metric.name} event", Toast.LENGTH_SHORT).show()
    }

    fun registerForInAppForms() = Klaviyo.registerForInAppForms()

    fun unregisterFromInAppForms() = Klaviyo.unregisterFromInAppForms()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        //Fetches the current push token and registers with Klaviyo Push-FCM
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Klaviyo.setPushToken(it)
        }

        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission revoked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
