package com.klaviyo.analytics

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.klaviyo.analytics.linking.DeepLinkHandler
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.KlaviyoApiClient
import com.klaviyo.analytics.state.KlaviyoState
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateSideEffects
import com.klaviyo.core.Operation
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.LifecycleException
import com.klaviyo.core.safeApply
import com.klaviyo.core.safeCall
import com.klaviyo.core.utils.takeIf
import java.io.Serializable
import java.util.LinkedList
import java.util.Queue

/**
 * Public API for the core Klaviyo SDK.
 * Receives profile changes and analytics requests
 * to be processed and sent to the Klaviyo backend
 */
object Klaviyo {

    /**
     * Queue of failed operations attempted prior to [initialize]
     */
    private val preInitQueue: Queue<Operation<Unit>> = LinkedList()

    /**
     * This method is provided for apps that are unable to register their API key immediately
     * on app launch in order enable limited SDK functionality including tracking app lifecycle,
     * detection of permission changes, and handling universal tracking links.
     *
     * Your API key still must be provided as early as possible for full SDK functionality!
     *
     * @param applicationContext
     */
    @JvmStatic
    fun registerForLifecycleCallbacks(applicationContext: Context) = safeApply {
        if (!Registry.isRegistered<Config>()) {
            // Register a partial config, missing API Key, to allow lifecycle tracking and context access for partial functionality
            Registry.register<Config>(
                Registry.configBuilder
                    .applicationContext(applicationContext)
                    .build()
            )
        }

        // Some APIs (such as deep linking) work without an API key, so we can register the core service now
        Registry.registerOnce<ApiClient> { KlaviyoApiClient }

        // Register lifecycle callbacks to monitor app foreground/background state
        applicationContext.applicationContext.takeIf<Application>()?.apply {
            unregisterActivityLifecycleCallbacks(Registry.lifecycleCallbacks)
            unregisterComponentCallbacks(Registry.componentCallbacks)
            registerActivityLifecycleCallbacks(Registry.lifecycleCallbacks)
            registerComponentCallbacks(Registry.componentCallbacks)
        } ?: throw LifecycleException()
    }

    /**
     * Configure Klaviyo SDK with your account's public API Key and application context.
     * This must be called to before using any other SDK functionality
     *
     * @param apiKey Your Klaviyo account's public API Key
     * @param applicationContext
     */
    @JvmStatic
    fun initialize(apiKey: String, applicationContext: Context) = safeApply {
        Registry.register<Config>(
            Registry.configBuilder
                .apiKey(apiKey)
                .applicationContext(applicationContext)
                .build()
        )

        registerForLifecycleCallbacks(applicationContext)

        Registry.registerOnce<State> {
            KlaviyoState().also { state ->
                Registry.register<StateSideEffects>(StateSideEffects(state))
            }
        }

        Registry.get<ApiClient>().startService()

        Registry.get<State>().apiKey = apiKey

        if (preInitQueue.isNotEmpty()) {
            Registry.log.info(
                "Replaying ${preInitQueue.count()} operation(s) invoked prior to Klaviyo initialization."
            )

            while (preInitQueue.isNotEmpty()) {
                preInitQueue.poll()?.let { safeCall(null, it) }
            }
        }
    }

    /**
     * Registers a [DeepLinkHandler] to be invoked whenever a deep link is opened, including:
     * - When a Klaviyo push notification bearing a deep link is opened
     * - When an In-App Form deep link action is triggered
     * - When a Universal link with Klaviyo click-tracking is opened
     *
     * When registered, this takes the place of the default SDK behavior, which is to broadcast
     * an Intent with the deep link URL back to the host application.
     */
    @JvmStatic
    fun registerDeepLinkHandler(handler: DeepLinkHandler) = safeApply {
        Registry.register<DeepLinkHandler>(handler)
    }

    /**
     * Removes any registered [DeepLinkHandler], reverting to the default SDK behavior of
     * broadcasting an Intent with the deep link URL back to the host application.
     */
    @JvmStatic
    fun unregisterDeepLinkHandler() = safeApply {
        Registry.unregister<DeepLinkHandler>()
    }

    /**
     * Assign new identifiers and attributes to the currently tracked profile.
     * If a profile has already been identified, it will be overwritten by calling [resetProfile].
     *
     * Whitespace will be trimmed from all identifier values.
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * @param profile A map-like object representing properties of the new user
     * @return Returns [Klaviyo] for call chaining
     */
    @JvmStatic
    fun setProfile(profile: Profile): Klaviyo = safeApply {
        Registry.get<State>().setProfile(profile)
    }

    /**
     * Assigns an email address to the currently tracked Klaviyo profile
     *
     * Whitespace will be trimmed from the value.
     * Calling this method with an empty string or the same value as currently set
     * will be ignored. To clear identifiers, use [resetProfile].
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param email Email address for active user
     * @return Returns [Klaviyo] for call chaining
     */
    @JvmStatic
    fun setEmail(email: String): Klaviyo = this.setProfileAttribute(ProfileKey.EMAIL, email)

    /**
     * @return The email of the currently tracked profile, if set
     */
    @JvmStatic
    fun getEmail(): String? = safeCall { Registry.get<State>().email }

    /**
     * Assigns a phone number to the currently tracked Klaviyo profile
     *
     * NOTE: Phone number format is not validated, but should conform to Klaviyo formatting
     * see (documentation)[https://help.klaviyo.com/hc/en-us/articles/360046055671-Accepted-phone-number-formats-for-SMS-in-Klaviyo]
     *
     * Whitespace will be trimmed from the value.
     * Calling this method with an empty string or the same value as currently set
     * will be ignored. To clear identifiers, use [resetProfile].
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param phoneNumber Phone number for active user
     * @return Returns [Klaviyo] for call chaining
     */
    @JvmStatic
    fun setPhoneNumber(phoneNumber: String): Klaviyo =
        this.setProfileAttribute(ProfileKey.PHONE_NUMBER, phoneNumber)

    /**
     * @return The phone number of the currently tracked profile, if set
     */
    @JvmStatic
    fun getPhoneNumber(): String? = safeCall { Registry.get<State>().phoneNumber }

    /**
     * Assigns a unique identifier to associate the currently tracked Klaviyo profile
     * with a profile in an external system, such as a point-of-sale system.
     *
     * NOTE: Please consult (documentation)[https://help.klaviyo.com/hc/en-us/articles/12902308138011-Understanding-identity-resolution-in-Klaviyo-]
     * to familiarize yourself with identity resolution before using this identifier.
     *
     * Whitespace will be trimmed from the value.
     * Calling this method with an empty string or the same value as currently set
     * will be ignored. To clear identifiers, use [resetProfile].
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param externalId Unique identifier from external system
     * @return Returns [Klaviyo] for call chaining
     */
    @JvmStatic
    fun setExternalId(externalId: String): Klaviyo =
        this.setProfileAttribute(ProfileKey.EXTERNAL_ID, externalId)

    /**
     * @return The external ID of the currently tracked profile, if set
     */
    @JvmStatic
    fun getExternalId(): String? = safeCall { Registry.get<State>().externalId }

    /**
     * Saves a push token and registers to the current profile
     *
     * We append this token to a property map and queue it into an identify request to send to
     * the Klaviyo asynchronous APIs.
     * We then write it into the shared preferences so that we can fetch the token for this
     * device as needed
     *
     * @param pushToken The push token provided by the device push service
     */
    @JvmStatic
    fun setPushToken(pushToken: String) = safeApply { Registry.get<State>().pushToken = pushToken }

    /**
     * @return The device push token, if one has been assigned to currently tracked profile
     */
    @JvmStatic
    fun getPushToken(): String? = safeCall { Registry.get<State>().pushToken }

    /**
     * Assign an attribute to the currently tracked profile by key/value pair
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called when you collect additional data about your user
     * (e.g. first and last name, or location)
     *
     * @param propertyKey
     * @param value
     * @return Returns [Klaviyo] for call chaining
     */
    @JvmStatic
    fun setProfileAttribute(propertyKey: ProfileKey, value: Serializable): Klaviyo = safeApply {
        Registry.get<State>().setAttribute(propertyKey, value)
    }

    /**
     * Clears all stored profile identifiers (e.g. email or phone) and starts a new tracked profile
     *
     * This should be called whenever an active user in your app is removed
     * (e.g. after a logout)
     */
    @JvmStatic
    fun resetProfile() = safeApply { Registry.get<State>().reset() }

    /**
     * Creates an [Event] associated with the currently tracked profile
     *
     * @param event A map-like object representing the event attributes
     * @return Returns [Klaviyo] for call chaining
     */
    @JvmStatic
    fun createEvent(event: Event): Klaviyo = safeApply {
        Registry.get<State>().createEvent(event, Registry.get<State>().getAsProfile())
    }

    /**
     * Creates an [Event] associated with the currently tracked profile
     *
     * Convenience method for creating an event with a metric, optional value, and no other properties
     *
     * @param metric [EventMetric] to create
     * @param value [Double?] value to assign the event
     * @return Returns [Klaviyo] for call chaining
     */
    @JvmStatic
    @JvmOverloads
    fun createEvent(metric: EventMetric, value: Double? = null): Klaviyo =
        createEvent(Event(metric).setValue(value))

    /**
     * From an opened push Intent, creates an [EventMetric.OPENED_PUSH] [Event]
     * containing appropriate tracking parameters
     *
     * While it is generally required to [initialize] before interacting with the Klaviyo SDK,
     * due to potential timing issues push open events are stored in an in-memory buffer prior to initializing,
     * and will be ingested once you initialize with your public API key.
     *
     * @param intent the [Intent] from opening a notification
     */
    @JvmStatic
    fun handlePush(intent: Intent?): Klaviyo = this
        .takeIf { intent.isKlaviyoNotificationIntent }
        ?.safeApply(preInitQueue) {
            // Create and enqueue an $opened_push
            val event = Event(EventMetric.OPENED_PUSH)
            val extras = intent?.extras

            extras?.keySet()?.forEach { key ->
                if (key.contains("com.klaviyo")) {
                    val eventKey = EventKey.CUSTOM(key.replace("com.klaviyo.", ""))
                    event[eventKey] = extras.getString(key, "")
                }
            }

            Registry.get<State>().pushToken?.let { event[EventKey.PUSH_TOKEN] = it }

            // Not using createEvent here to avoid nested safeApply calls
            Registry.get<State>().createEvent(event, Registry.get<State>().getAsProfile())
        }?.safeApply {
            // If a Klaviyo notification is deep linked, invoke the developer's deep link handler
            // if registered. If not, do nothing. The host already received the appropriate intent.
            intent?.data?.takeIf {
                DeepLinking.isHandlerRegistered
            }?.let { uri ->
                DeepLinking.handleDeepLink(uri)
            }
        }
        ?: apply {
            Registry.log.verbose("Non-Klaviyo intent ignored")
        }

    /**
     * Handles a universal link [Intent], by resolving the destination [Uri] asynchronously
     * and invoking the registered [DeepLinkHandler] or sending the host application an [Intent]
     *
     * @return [Boolean] Indicating whether the url is a Klaviyo tracking link,
     *         and the destination url is being resolved asynchronously
     */
    @JvmStatic
    fun handleUniversalTrackingLink(url: String): Boolean = safeCall {
        try {
            DeepLinking.handleUniversalTrackingLink(url.toUri())
        } catch (e: Exception) {
            Registry.log.error("Failed handling universal link: $url", e)
            false
        }
    } ?: false

    /**
     * Handles a universal link [Intent], by resolving the destination [Uri] asynchronously
     * and invoking the registered [DeepLinkHandler] or sending the host application an [Intent]
     *
     * @return [Boolean] Indicating whether the url is a Klaviyo tracking link,
     *         and the destination url is being resolved asynchronously
     */
    @JvmStatic
    fun handleUniversalTrackingLink(intent: Intent?): Boolean = safeCall {
        intent?.data?.let { uri ->
            DeepLinking.handleUniversalTrackingLink(uri)
        }
    } ?: intent.isKlaviyoUniversalTrackingIntent

    /**
     * Checks whether a notification intent originated from Klaviyo.
     * Java-friendly static method.
     */
    @JvmStatic
    @Deprecated(
        "Use isKlaviyoNotificationIntent instead, will be removed in the next major version",
        ReplaceWith("isKlaviyoNotificationIntent")
    )
    fun isKlaviyoIntent(intent: Intent): Boolean = intent.isKlaviyoNotificationIntent

    /**
     * Checks whether a notification intent originated from Klaviyo
     */
    @Deprecated(
        "Use isKlaviyoNotificationIntent instead, will be removed in the next major version",
        ReplaceWith("isKlaviyoNotificationIntent")
    )
    val Intent.isKlaviyoIntent: Boolean
        @JvmName("_isKlaviyoIntent")
        get() = this.isKlaviyoNotificationIntent

    /**
     * Checks whether a notification intent originated from Klaviyo.
     * Java-friendly static method.
     */
    @JvmStatic
    fun isKlaviyoNotificationIntent(intent: Intent?): Boolean =
        intent?.getStringExtra("com.klaviyo._k")?.isNotEmpty() ?: false

    /**
     * Checks whether a notification intent originated from Klaviyo
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val Intent?.isKlaviyoNotificationIntent: Boolean
        @JvmName("_isKlaviyoNotificationIntent")
        get() = this?.getStringExtra("com.klaviyo._k")?.isNotEmpty() ?: false

    /**
     * Determine if an intent is a Klaviyo click-tracking universal/app link.
     * Java-friendly static method.
     */
    @JvmStatic
    fun isKlaviyoUniversalTrackingIntent(intent: Intent?): Boolean =
        intent?.data?.isKlaviyoUniversalTrackingUri == true

    /**
     * Determine if an intent is a Klaviyo click-tracking universal/app link
     */
    val Intent?.isKlaviyoUniversalTrackingIntent: Boolean
        @JvmName("_isKlaviyoUniversalTrackingIntent")
        get() = this?.data?.isKlaviyoUniversalTrackingUri == true

    /**
     * Determine if a URI is a Klaviyo click-tracking universal/app link.
     * Java-friendly static method.
     */
    @JvmStatic
    fun isKlaviyoUniversalTrackingUri(uri: Uri): Boolean =
        DeepLinking.isUniversalTrackingUri(uri)

    /**
     * Determine if a URI is a Klaviyo click-tracking universal/app link
     */
    val Uri.isKlaviyoUniversalTrackingUri: Boolean
        @JvmName("_isKlaviyoUniversalTrackingUri")
        get() = DeepLinking.isUniversalTrackingUri(this)
}
