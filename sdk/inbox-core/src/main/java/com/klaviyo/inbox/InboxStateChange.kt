package com.klaviyo.inbox

/**
 * A change to a message's read or archived state. Read and archived are independent, so archiving
 * a message leaves it read or unread.
 */
enum class InboxStateChange {
    MARK_READ,
    MARK_UNREAD,
    ARCHIVE,
    UNARCHIVE
}
