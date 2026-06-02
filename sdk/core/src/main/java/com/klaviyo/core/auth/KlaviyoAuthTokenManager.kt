package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.safeLaunch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * @param lifecycleMonitor declared in the constructor to lock the signature for MAGE-629's
 *   lifecycle-aware refresh work; unused by code in this PR.
 */
internal class KlaviyoAuthTokenManager(
    @Suppress("unused") private val lifecycleMonitor: LifecycleMonitor = Registry.lifecycleMonitor
) : AuthTokenManager {

    // Internal (not on the interface) so MAGE-619 consumers are forced to use their own scope
    // when calling currentToken(), binding auth work to the correct lifecycle.
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Registry.dispatcher)

    // Guards the read-validate-write transition on both cachedToken and inFlightFetch, ensuring
    // exactly one Deferred is created when multiple callers miss the cache simultaneously.
    private val mutex = Mutex()

    // @Volatile so reads in invokeProvider (outside the mutex) always observe the latest write
    // from registerProvider. Single-write-wins semantics are acceptable for the happy path.
    @Volatile private var provider: AuthTokenProvider? = null

    @Volatile private var cachedToken: ValidatedToken? = null

    // Shared in-flight fetch deferred. All concurrent callers that miss the cache await this
    // single Deferred rather than each invoking the provider independently. Cleared (via
    // invokeOnCompletion) on both success and failure so the next request starts a fresh fetch.
    // @Volatile because registerProvider and invokeOnCompletion clear it without holding the
    // mutex; @Volatile ensures those writes are visible to the mutex-protected read in currentToken.
    @Volatile private var inFlightFetch: Deferred<ValidatedToken>? = null

    // Reserved for MAGE-630 (onTokenRefresh/offTokenRefresh observers). Matches the established
    // SDK observer-collection pattern (CopyOnWriteArrayList for thread-safe iteration while
    // observers add/remove themselves on arbitrary threads).
    @Suppress("unused")
    private val refreshObservers = CopyOnWriteArrayList<TokenRefreshObserver>()

    override fun registerProvider(provider: AuthTokenProvider) {
        // Cancel any in-flight fetch for the old provider before swapping.
        // Two complementary guards prevent a stale token from reaching the cache:
        //   1. If the cancelled coroutine is still inside invokeProvider(), the isActive check in
        //      suspendCancellableCoroutine drops any late onSuccess/onFailure callback.
        //   2. If the callback already fired and doFetch() is past invokeProvider() but hasn't
        //      written the cache yet, the next suspension point (mutex.withLock in doFetch)
        //      will observe the cancellation and throw CancellationException before the write.
        inFlightFetch?.cancel()
        inFlightFetch = null
        cachedToken = null
        this.provider = provider
        Registry.log.info("AuthTokenProvider registered")
        scope.safeLaunch { tryEagerFetch() }
    }

    private suspend fun tryEagerFetch() {
        try {
            currentToken(AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS)
        } catch (e: CancellationException) {
            // Preserve structured concurrency by rethrowing cancellation.
            throw e
        } catch (_: Exception) {
            // The failure is already logged at ERROR by validateOrThrow, by the timeout path, or
            // surfaced by the provider's own onFailure. Avoid double-logging at WARNING here.
            Registry.log.debug("Eager auth token fetch failed")
        }
    }

    override suspend fun currentToken(timeoutMs: Long): ValidatedToken {
        // Optimistic read of @Volatile field — no lock needed for the fast path.
        val cached = cachedToken
        if (cached != null && isStillValid(cached)) return cached

        // Atomic read-or-create of the in-flight deferred. The mutex ensures exactly one
        // scope.async { } is launched when multiple callers miss the cache simultaneously.
        val deferred: Deferred<ValidatedToken> = mutex.withLock {
            // Re-check under the lock; a concurrent caller may have populated the cache while
            // we waited. Non-local return from this inline lambda exits currentToken() directly.
            val freshenedCache = cachedToken
            if (freshenedCache != null && isStillValid(freshenedCache)) return freshenedCache

            inFlightFetch ?: scope.async { doFetch() }.also { d ->
                inFlightFetch = d
                // Reference-identity check: prevents a stale deferred's completion handler from
                // clearing a freshly-created deferred after a concurrent provider swap.
                d.invokeOnCompletion { if (inFlightFetch === d) inFlightFetch = null }
            }
        }

        // Each caller races its own timeout budget against the shared deferred. Timing out does
        // NOT cancel the underlying task — other callers with a larger budget still benefit if
        // the provider eventually responds.
        return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: run {
            val error = AuthTokenException.TimedOut
            Registry.log.error(requireNotNull(error.message), error)
            throw error
        }
    }

    /**
     * Invoke the provider, validate the returned JWT, write to the cache, and return the token.
     * Runs inside [scope].async so failures (provider error, validation error) are captured by
     * the Deferred and re-thrown to all awaiting callers.
     */
    private suspend fun doFetch(): ValidatedToken {
        val jwt = invokeProvider()
        val token = validateOrThrow(jwt)
        mutex.withLock { cachedToken = token }
        Registry.log.info(
            "Auth token acquired (exp=${token.expiresAtEpochSeconds}, iat=${token.issuedAtEpochSeconds})"
        )
        return token
    }

    private suspend fun invokeProvider(): String = suspendCancellableCoroutine { continuation ->
        val callback = object : AuthTokenProvider.Callback {
            override fun onSuccess(jwt: String) {
                if (continuation.isActive) continuation.resume(jwt)
            }

            override fun onFailure(error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
        provider?.fetchToken(callback) ?: continuation.resumeWithException(
            AuthTokenException.NoProviderRegistered
        )
    }

    private fun validateOrThrow(jwt: String): ValidatedToken =
        when (val result = JWTParser.parseAndValidate(jwt)) {
            is JWTValidationResult.Valid -> result.token
            else -> {
                val reason = result::class.simpleName ?: "Unknown"
                val error = AuthTokenException.ValidationFailed(reason)
                Registry.log.error(requireNotNull(error.message), error)
                throw error
            }
        }

    private fun isStillValid(token: ValidatedToken): Boolean {
        val now = Registry.clock.currentTimeMillis() / 1000L
        return now < token.expiresAtEpochSeconds - JWTParser.DEFAULT_LEEWAY_SECONDS
    }
}
