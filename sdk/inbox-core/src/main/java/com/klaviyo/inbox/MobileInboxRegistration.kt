package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

/**
 * Enable Mobile Inbox.
 *
 * Once registered, the SDK records standard Klaviyo push notifications delivered to this app
 * in an on-device inbox. Registration persists across app launches: capture continues until
 * [unregisterFromMobileInbox] is called. Calling this again replaces the stored [config].
 *
 * Messages are only captured while notification permission is granted.
 *
 * Note: a public API key is required, so [Klaviyo.initialize] must be called first.
 *
 * @param config see [MobileInboxConfig] for configuration options.
 * @throws MissingKlaviyoModule if the `com.klaviyo:inbox` module is not on the classpath.
 */
fun Klaviyo.registerForMobileInbox(
    config: MobileInboxConfig = MobileInboxConfig()
): Klaviyo = Registry.getOrNull<MobileInboxProvider>()?.let { provider ->
    safeApply { provider.register(config) }
} ?: throw MissingKlaviyoModule("inbox")

/**
 * Disable Mobile Inbox.
 *
 * Stops capturing push notifications and deletes all messages stored in the on-device inbox.
 * Registering again starts with an empty inbox.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:inbox` module is not on the classpath.
 */
fun Klaviyo.unregisterFromMobileInbox(): Klaviyo =
    Registry.getOrNull<MobileInboxProvider>()?.let { provider ->
        safeApply { provider.unregister() }
    } ?: throw MissingKlaviyoModule("inbox")

/**
 * Java-friendly static methods for Mobile Inbox.
 * Kotlin users should use the extension functions on [Klaviyo] instead.
 */
object KlaviyoInbox {
    /**
     * Enable Mobile Inbox.
     * Java-friendly static method.
     *
     * @param config see [MobileInboxConfig] for configuration options.
     * @see Klaviyo.registerForMobileInbox
     */
    @JvmStatic
    @JvmOverloads
    fun registerForMobileInbox(config: MobileInboxConfig = MobileInboxConfig()) {
        Klaviyo.registerForMobileInbox(config)
    }

    /**
     * Disable Mobile Inbox and delete stored messages.
     * Java-friendly static method.
     *
     * @see Klaviyo.unregisterFromMobileInbox
     */
    @JvmStatic
    fun unregisterFromMobileInbox() {
        Klaviyo.unregisterFromMobileInbox()
    }
}
