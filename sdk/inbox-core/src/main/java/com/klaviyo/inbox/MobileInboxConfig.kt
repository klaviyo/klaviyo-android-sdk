package com.klaviyo.inbox

import com.klaviyo.core.Registry

/**
 * Configuration for Mobile Inbox
 *
 * @param localRetentionLimit Maximum number of messages kept in the on-device inbox store.
 *  When the store exceeds this limit, the oldest messages are removed first.
 *  Defaults to [DEFAULT_LOCAL_RETENTION_LIMIT]. Values outside
 *  [MIN_LOCAL_RETENTION_LIMIT]..[MAX_LOCAL_RETENTION_LIMIT] are clamped to that range.
 */
class MobileInboxConfig @JvmOverloads constructor(
    localRetentionLimit: Int = DEFAULT_LOCAL_RETENTION_LIMIT
) {
    companion object {
        const val DEFAULT_LOCAL_RETENTION_LIMIT = 100
        const val MIN_LOCAL_RETENTION_LIMIT = 1
        const val MAX_LOCAL_RETENTION_LIMIT = 500
    }

    /**
     * The effective retention limit, clamped to the supported range
     */
    val localRetentionLimit: Int =
        localRetentionLimit.coerceIn(MIN_LOCAL_RETENTION_LIMIT, MAX_LOCAL_RETENTION_LIMIT).also {
            if (it != localRetentionLimit) {
                Registry.log.warning(
                    "localRetentionLimit $localRetentionLimit is outside the supported range " +
                        "$MIN_LOCAL_RETENTION_LIMIT..$MAX_LOCAL_RETENTION_LIMIT, $it will be used instead."
                )
            }
        }

    override fun equals(other: Any?): Boolean =
        other is MobileInboxConfig && other.localRetentionLimit == localRetentionLimit

    override fun hashCode(): Int = localRetentionLimit

    override fun toString(): String = "MobileInboxConfig(localRetentionLimit=$localRetentionLimit)"
}
