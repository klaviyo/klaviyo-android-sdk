package com.klaviyo.core

import java.util.Queue

typealias Operation<T> = () -> T

/**
 * Base class for exceptions thrown within the Klaviyo SDK
 *
 * @property message
 */
abstract class KlaviyoException(final override val message: String) : Exception(message)

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
    Registry.log.error(e.message, e)
    errorQueue?.add(block)
    null
}

/**
 * Safe apply function that logs [KlaviyoException] rather than crash
 */
fun <Caller> Caller.safeApply(
    errorQueue: Queue<Operation<Unit>>? = null,
    block: Operation<Unit>
) = apply { safeCall(errorQueue, block) }
