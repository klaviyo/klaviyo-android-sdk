package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.core.Registry

/**
 * Read access to the on-device inbox.
 *
 * Every call is safe from the main thread. A store failure surfaces as [InboxException].
 */
interface MobileInbox {
    /**
     * Count the current profile's messages that are unread and not archived. Zero when nothing
     * has been captured yet.
     */
    suspend fun fetchUnreadCount(): Int
}

/**
 * Access to the Mobile Inbox.
 *
 * @throws MissingKlaviyoModule if the `com.klaviyo:inbox` module is not on the classpath.
 */
val Klaviyo.mobileInbox: MobileInbox
    get() = Registry.getOrNull<MobileInboxProvider>()?.mobileInbox
        ?: throw MissingKlaviyoModule("inbox")
