package com.klaviyo.core.networking

/**
 * Base interface for API client functionality
 *
 * Defines generic queue management operations that don't depend on
 * analytics-specific types. This allows networking infrastructure
 * to be managed at the core level while specific request types
 * remain in their respective modules.
 */
interface BaseApiClient {

    /**
     * Launch the API client service
     * Should be idempotent in case of re-initialization
     */
    fun startService()

    /**
     * Tell the client to write its queue to the persistent store
     */
    fun persistQueue()

    /**
     * Tell the client to restore its queue from the persistent store engine
     */
    fun restoreQueue()

    /**
     * Tell the client to attempt to flush network request queue now
     */
    fun flushQueue()
}
