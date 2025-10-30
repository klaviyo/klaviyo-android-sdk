package com.klaviyo.core

import com.klaviyo.core.config.KlaviyoConfig
import java.util.Queue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

typealias Operation<T> = () -> T

/**
 * Base class for exceptions thrown within the Klaviyo SDK
 *
 * @property message
 */
abstract class KlaviyoException(final override val message: String) : Exception(message)

/**
 * Safely perform an operation, catch and log Exceptions rather than crash.
 * Primarily meant to catch [KlaviyoException] as a guard against invalid operations such as invoking Klaviyo prior to initializing.
 * In production, this also safeguards the host app against crashing due to uncaught exceptions in Klaviyo code.
 *
 * Note: Take care not to nest [safeCall] / [safeApply] invocations, because the inner exception
 * will not halt execution of the outer method.
 *
 * Warning: This is not a substitute for proper exception handling at the call site,
 * reserve this for safeguarding against wholly unexpected failures.
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
} catch (e: Throwable) {
    if (KlaviyoConfig.isDebugBuild) {
        // To avoid development blindness, re-throw uncaught exceptions in DEBUG builds
        throw e
    } else {
        Registry.log.error("Caught unhandled exception:", e)
        null
    }
}

/**
 * Safely perform an operation, catch and log Exceptions rather than crash.
 * Primarily meant to catch [KlaviyoException] as a guard against invalid operations such as invoking Klaviyo prior to initializing.
 * In production, this also safeguards the host app against crashing due to uncaught exceptions in Klaviyo code.
 *
 * Note: Take care not to nest [safeCall] / [safeApply] invocations, because the inner exception
 * will not halt execution of the outer method.
 *
 * Warning: This is not a substitute for proper exception handling at the call site,
 * reserve this for safeguarding against wholly unexpected failures.
 */
fun <Caller> Caller.safeApply(
    errorQueue: Queue<Operation<Unit>>? = null,
    block: Operation<Unit>
) = apply { safeCall(errorQueue, block) }

/**
 * Launch a coroutine to safely perform a suspending operation and catch and log Exceptions rather than crash.
 * Primarily meant to catch [KlaviyoException] as a guard against invalid operations such as invoking Klaviyo prior to initializing.
 * In production, this also safeguards the host app against crashing due to uncaught exceptions in Klaviyo code.
 *
 * Warning: This is not a substitute for proper exception handling at the call site,
 * reserve this for safeguarding against wholly unexpected failures.
 */
fun CoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job = launch(
    context + CoroutineExceptionHandler { _, e ->
        when (e) {
            is KlaviyoException -> Registry.log.error(e.message, e)
            else -> if (KlaviyoConfig.isDebugBuild) {
                // To avoid development blindness, re-throw uncaught exceptions in DEBUG builds
                throw e
            } else {
                Registry.log.error("Caught unhandled exception in coroutine scope:", e)
            }
        }
    },
    start,
    block
)
