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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.forms.registerForInAppForms
import com.klaviyo.forms.unregisterFromInAppForms
import com.klaviyo.pushFcm.KlaviyoAlarmScheduler
import com.klaviyo.sample.ui.theme.KlaviyoandroidsdkTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class SampleActivity : ComponentActivity() {
    private val externalId = mutableStateOf(Klaviyo.getExternalId() ?: "")
    private val email = mutableStateOf(Klaviyo.getEmail() ?: "")
    private val phoneNumber = mutableStateOf(Klaviyo.getPhoneNumber() ?: "")
    private val pushToken = mutableStateOf(Klaviyo.getPushToken() ?: "")
    private val notificationPermission = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KlaviyoandroidsdkTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SampleView(
                        externalId,
                        email,
                        phoneNumber,
                        pushToken,
                        notificationPermission,
                        setProfile = ::setProfile,
                        resetProfile = ::resetProfile,
                        createTestEvent = ::createTestEvent,
                        createViewedProductEvent = ::createViewedProductEvent,
                        registerForInAppForms = ::registerForInAppForms,
                        unregisterFromInAppForms = ::unregisterFromInAppForms,
                        requestPermission = ::askNotificationPermission,
                        scheduleNotification = ::scheduleTestNotification,
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
        notificationPermission.value = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun setProfile() {
        Klaviyo
            .setExternalId(externalId.value)
            .setEmail(email.value)
            .setPhoneNumber(phoneNumber.value)

        Toast.makeText(this, "Profile set", Toast.LENGTH_SHORT).show()
    }

    private fun resetProfile() {
        externalId.value = ""
        email.value = ""
        phoneNumber.value = ""

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

    /**
     * Creates and sends a test notification with an intended_send_time set to one minute in the future
     * This demonstrates the scheduled notification feature without requiring server-side changes
     */
    private fun scheduleTestNotification() {
        // Create a calendar instance set to 1 minute in the future, rounded to nearest minute
        val calendar = Calendar.getInstance()
        val currentMinute = calendar.get(Calendar.MINUTE)
        calendar.set(Calendar.MINUTE, currentMinute + 1)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Get the scheduled time in device timezone for display
        val displayTime = calendar.time
        val scheduledTimeMillis = displayTime.time
        
        // Format a readable time for the notification body
        val readableTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(displayTime)

        // Use the helper method to schedule the notification directly
        val success = KlaviyoAlarmScheduler.scheduleTestNotification(
            context = applicationContext,
            title = "Scheduled Notification",
            body = "This notification was scheduled to appear at $readableTime",
            scheduledTimeMillis = scheduledTimeMillis
        )

        Toast.makeText(
            this,
            if (success) "Scheduled notification for $readableTime" else "Failed to schedule notification",
            Toast.LENGTH_LONG
        ).show()
    }

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
