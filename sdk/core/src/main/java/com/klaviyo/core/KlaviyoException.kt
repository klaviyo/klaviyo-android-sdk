package com.klaviyo.core

import java.util.Queue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

typealias Operation<T> = () -> T
typealias SuspendOperation = suspend CoroutineScope.() -> Unit

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
    errorQueue?.apply {
        Registry.log.warning("The operation will be retried.", e)
        add(block)
    } ?: run {
        Registry.log.error(e.message, e)
    }
    null
}

/**
 * Safe apply function that logs [KlaviyoException] rather than crash
 */
fun <Caller> Caller.safeApply(
    errorQueue: Queue<Operation<Unit>>? = null,
    block: Operation<Unit>
) = apply { safeCall(errorQueue, block) }

/**
 * Launch a coroutine wrapped in a generic exception handler
 */
fun CoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    errorQueue: Queue<SuspendOperation>? = null,
    block: SuspendOperation
): Job = launch(context, start) {
    try {
        block()
    } catch (e: KlaviyoException) {
        errorQueue?.apply {
            Registry.log.warning("The operation will be retried.", e)
            add(block)
        } ?: run {
            Registry.log.error(e.message, e)
        }
        null
    }
}
