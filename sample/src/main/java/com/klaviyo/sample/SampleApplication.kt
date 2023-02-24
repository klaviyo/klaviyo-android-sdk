package com.klaviyo.sample

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.push_fcm.KlaviyoPushService

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Klaviyo.initialize("KLAVIYO_PUBLIC_API_KEY", applicationContext)

        registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)

        setAttributesExample()
        createEventExample()
        setPushTokenExample()
    }

    private fun setProfileExample() {
        val profile = Profile(mapOf(
            ProfileKey.EMAIL to "kermit@example.com",
            ProfileKey.FIRST_NAME to "Kermit"
        ))

        Klaviyo.setProfile(profile)
    }

    private fun setAttributesExample() {
        //Start a profile for Kermit
        Klaviyo.setEmail("kermit@example.com")
            .setPhoneNumber("+12223334444")
            .setExternalId("USER_IDENTIFIER")
            .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")
            .setProfileAttribute(ProfileKey.CUSTOM("instrument"), "banjo")
    }

    private fun resetProfileExample() {
        //Stop tracking Kermit
        Klaviyo.resetProfile()

        //Start new profile for Robin with new IDs
        Klaviyo.setEmail("robin@example.com")
            .setPhoneNumber("+5556667777")
            .setProfileAttribute(ProfileKey.FIRST_NAME, "Robin")
    }

    private fun createEventExample() {
        val event = Event(EventType.VIEWED_PRODUCT)
            .setProperty(EventKey.VALUE, "100")
            .setProperty(EventKey.CUSTOM("custom_key"), "value")

        Klaviyo.createEvent(event)
    }

    private fun pushFcmExample() {
        //Fetches the current push token and registers with Klaviyo Push-FCM
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            KlaviyoPushService.setPushToken(it)
        }
    }

    private fun setPushTokenExample() {
        //If you choose to interface with FirebaseMessaging yourself, you can omit Push-FCM package
        Klaviyo.setPushToken("FCM_PUSH_TOKEN")
    }
}