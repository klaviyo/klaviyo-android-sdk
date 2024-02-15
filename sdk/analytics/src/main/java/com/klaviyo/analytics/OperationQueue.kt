package com.klaviyo.analytics

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.KlaviyoException
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock

internal class OperationQueue {
    /**
     * Debounce timer for enqueuing profile API calls
     */
    private var timer: Clock.Cancellable? = null

    /**
     * Pending batch of profile updates to be merged into one API call
     */
    private var pendingProfile: Profile? = null

    /**
     * Uses debounce mechanism to merge profile changes
     * within a short span of time into one API transaction
     *
     * @param profile Incoming profile attribute changes
     */
    fun debounceProfileUpdate(profile: Profile) {
        // Log for traceability
        val operation = pendingProfile?.let { "Merging" } ?: "Starting"
        Registry.log.info("$operation profile update")

        // Merge new changes into pending transaction
        pendingProfile = pendingProfile?.merge(profile) ?: profile

        // Add current identifiers from UserInfo to pending transaction
        pendingProfile = UserInfo.getAsProfile().merge(pendingProfile!!)

        // Reset timer
        timer?.cancel()
        timer = Registry.clock.schedule(Registry.config.debounceInterval.toLong()) {
            flushProfile()
        }
    }

    /**
     * Enqueue pending profile changes as an API call and then clear slate
     */
    fun flushProfile() = pendingProfile?.let {
        timer?.cancel()
        Registry.log.info("Flushing profile update")
        Registry.get<ApiClient>().enqueueProfile(it)
        pendingProfile = null
    }
}

/**
 * Safely invoke a function and log KlaviyoExceptions rather than crash
 */
internal inline fun <T>safeCall(block: () -> T): T? {
    return try {
        block()
    } catch (e: KlaviyoException) {
        // KlaviyoException is self-logging
        null
    }
}

/**
 * Safe apply function that logs KlaviyoExceptions rather than crash
 */
internal inline fun Klaviyo.safeApply(block: () -> Unit) = apply { safeCall { block() } }
