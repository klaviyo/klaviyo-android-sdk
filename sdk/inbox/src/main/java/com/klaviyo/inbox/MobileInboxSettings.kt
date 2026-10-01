package com.klaviyo.inbox

import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.Registry
import org.json.JSONException
import org.json.JSONObject

/**
 * Mobile Inbox enablement and configuration, persisted so that capture in a cold-started
 * process honors the host's last registration choice.
 */
internal data class MobileInboxSettings(
    val enabled: Boolean,
    val localRetentionLimit: Int = MobileInboxConfig.DEFAULT_LOCAL_RETENTION_LIMIT
) {
    fun toJson(): String = JSONObject()
        .put(ENABLED_KEY, enabled)
        .put(LOCAL_RETENTION_LIMIT_KEY, localRetentionLimit)
        .toString()

    companion object {
        internal const val STORAGE_KEY = "klaviyo_mobile_inbox_settings"
        private const val ENABLED_KEY = "enabled"
        private const val LOCAL_RETENTION_LIMIT_KEY = "local_retention_limit"

        /**
         * Read persisted settings, or null if the inbox has never been registered.
         * Unreadable settings are treated as disabled.
         */
        fun load(): MobileInboxSettings? {
            val json = Registry.dataStore.fetch(STORAGE_KEY) ?: return null
            return try {
                JSONObject(json).let {
                    MobileInboxSettings(
                        enabled = it.getBoolean(ENABLED_KEY),
                        localRetentionLimit = MobileInboxConfig(
                            it.optInt(
                                LOCAL_RETENTION_LIMIT_KEY,
                                MobileInboxConfig.DEFAULT_LOCAL_RETENTION_LIMIT
                            )
                        ).localRetentionLimit
                    )
                }
            } catch (e: JSONException) {
                Registry.log.warning(
                    "Unable to read Mobile Inbox settings, treating as disabled",
                    e
                )
                MobileInboxSettings(enabled = false)
            }
        }

        fun save(settings: MobileInboxSettings) =
            Registry.dataStore.store(STORAGE_KEY, settings.toJson())
    }
}

/**
 * Whether a delivered push should be captured into the inbox:
 * the host has registered for Mobile Inbox and notification permission is granted.
 *
 * Reads only persisted settings, so it never opens the inbox store.
 */
internal fun isInboxCaptureEnabled(): Boolean =
    MobileInboxSettings.load()?.enabled == true && DeviceProperties.notificationPermissionGranted
