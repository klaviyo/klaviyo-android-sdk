package com.klaviyo.inbox

import com.klaviyo.core.Constants

/**
 * The part of a message the user tapped. A raw-value type so a later release can add a value
 * without breaking a host's exhaustive `when`.
 */
class InboxClickElement private constructor(val rawValue: String) {
    override fun equals(other: Any?): Boolean =
        other is InboxClickElement && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    override fun toString(): String = rawValue

    companion object {
        /** The message itself, which performs [InboxMessage.defaultDestination] */
        @JvmField
        val BODY = InboxClickElement(Constants.CLICK_ELEMENT_BODY)

        /** One of the message's [InboxMessage.actions] */
        @JvmField
        val BUTTON = InboxClickElement(Constants.CLICK_ELEMENT_BUTTON)
    }
}
