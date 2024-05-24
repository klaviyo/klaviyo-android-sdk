package com.klaviyo.core

/**
 * Exceptions that automatically hook into our logger
 *
 * @property message
 */
abstract class KlaviyoException(final override val message: String) : Exception(message) {
    init {
        log()
    }

    private fun log() = Registry.log.error(message, this)
}

/**
 * Safely invoke a function and log KlaviyoExceptions rather than crash
 * Take care not to nest [safeCall] invocations, because the inner exception
 * will not halt execution of the outer method.
 */
inline fun <T> safeCall(block: () -> T): T? {
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
inline fun <T> T.safeApply(block: () -> Unit) = apply { safeCall { block() } }
