package com.klaviyo.inbox

/**
 * Service interface for the Mobile Inbox module.
 *
 * The full `inbox` module registers its implementation via a ContentProvider at app startup.
 * When only `inbox-core` is present, no implementation is registered.
 */
interface MobileInboxProvider {
    /**
     * Enable Mobile Inbox capture with the given [config], persisting the choice across launches.
     */
    fun register(config: MobileInboxConfig)

    /**
     * Disable Mobile Inbox capture and delete the on-device inbox store.
     */
    fun unregister()
}
