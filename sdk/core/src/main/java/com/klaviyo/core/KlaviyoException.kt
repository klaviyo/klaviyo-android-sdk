package com.klaviyo.core

import java.util.Queue

typealias Operation<T> = () -> T

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
fun <ReturnType> safeCall(
    errorQueue: Queue<Operation<ReturnType>>? = null,
    block: Operation<ReturnType>
): ReturnType? = try {
    block()
} catch (e: KlaviyoException) {
    // KlaviyoException is self-logging
    errorQueue?.add(block).let { null }
}

/**
 * Safe apply function that logs [KlaviyoException] rather than crash
 */
fun <Caller> Caller.safeApply(
    errorQueue: Queue<Operation<Unit>>? = null,
    block: Operation<Unit>
) = apply { safeCall(errorQueue, block) }
