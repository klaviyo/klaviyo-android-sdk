package com.klaviyo.sample

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.push_fcm.KlaviyoPushService
import com.klaviyo.sample.ui.theme.KlaviyoandroidsdkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Klaviyo.initialize("KLAVIYO_PUBLIC_API_KEY", applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //TODO how do we instruct people below API 29?
            registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
        }

        //Fetches the current push token and registers with Push SDK
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            KlaviyoPushService.setPushToken(it)
        }

        setContent {
            KlaviyoandroidsdkTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting("Android")
                }
            }
        }

        val profile = Profile(mapOf(
            ProfileKey.EMAIL to "kermit@example.com",
            ProfileKey.FIRST_NAME to "Kermit"
        ))
        Klaviyo.setProfile(profile)

        Klaviyo.setEmail("kermit@example.com")
            .setPhoneNumber("+12223334444")
            .setExternalId("USER_IDENTIFIER")
            .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")
            .setProfileAttribute(ProfileKey.CUSTOM("instrument"), "banjo")

        //Start a profile for Kermit
        Klaviyo.setEmail("kermit@example.com")
            .setPhoneNumber("+12223334444")
            .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")

        //Stop tracking Kermit
        Klaviyo.resetProfile()

        //Start new profile for Robin with new IDs
        Klaviyo.setEmail("robin@example.com")
            .setPhoneNumber("+5556667777")
            .setProfileAttribute(ProfileKey.FIRST_NAME, "Robin")

        val event = Event(EventType.VIEWED_PRODUCT)
            .setProperty(EventKey.VALUE, "100")
            .setProperty(EventKey.CUSTOM("custom_key"), "value")
        Klaviyo.createEvent(event)

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        //Tracks when a system tray notification is opened
        KlaviyoPushService.handlePush(intent)
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    KlaviyoandroidsdkTheme {
        Greeting("Android")
    }
}