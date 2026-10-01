package com.klaviyo.inbox

import com.klaviyo.core.Registry
import com.klaviyo.core.safeLaunch
import kotlinx.coroutines.CoroutineScope

internal class KlaviyoMobileInboxProvider : MobileInboxProvider {
    override fun register(config: MobileInboxConfig) {
        MobileInboxSettings.save(
            MobileInboxSettings(enabled = true, localRetentionLimit = config.localRetentionLimit)
        )
        Registry.log.debug("Registered for Mobile Inbox with $config")
    }

    override fun unregister() {
        // Persist the disabled flag before deleting, so capture stops before the store is removed
        MobileInboxSettings.save(
            MobileInboxSettings(
                enabled = false,
                localRetentionLimit = MobileInboxSettings.load()?.localRetentionLimit
                    ?: MobileInboxConfig.DEFAULT_LOCAL_RETENTION_LIMIT
            )
        )
        CoroutineScope(Registry.dispatcher).safeLaunch { InboxStore.delete() }
        Registry.log.debug("Unregistered from Mobile Inbox")
    }
}
