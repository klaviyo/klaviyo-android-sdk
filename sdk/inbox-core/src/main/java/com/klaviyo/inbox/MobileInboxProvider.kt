package com.klaviyo.inbox

/**
 * Service interface for the Mobile Inbox module.
 *
 * The `inbox-core` module defines the interface. The full `inbox` module registers its
 * implementation via an `InboxInitProvider` ContentProvider at app startup.
 */
interface MobileInboxProvider {
    val mobileInbox: MobileInbox
}
