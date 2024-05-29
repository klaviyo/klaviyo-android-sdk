package com.klaviyo.core

/**
 * Base class for exceptions thrown within the Klaviyo SDK
 *
 * @property message
 */
abstract class KlaviyoException(final override val message: String) : Exception(message)

/**
 * Safely invoke a function and log KlaviyoExceptions rather than crash
 *
 * Take care not to nest [safeCall] invocations, because the inner exception
 * will not halt execution of the outer method.
 */
inline fun <T> safeCall(block: () -> T): T? {
    return try {
        block()
    } catch (e: KlaviyoException) {
        Registry.log.error(e.message, e)
        null
    }
}

/**
 * Safe apply function that logs KlaviyoExceptions rather than crash
 */
inline fun <T> T.safeApply(block: () -> Unit) = apply { safeCall { block() } }
