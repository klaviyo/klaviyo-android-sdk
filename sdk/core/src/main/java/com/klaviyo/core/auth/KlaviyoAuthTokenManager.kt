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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    // Mutex retained for currentToken's read-validate-write transition; MAGE-628 will replace
    // this with Deferred-based deduplication.
    private val mutex = Mutex()

    // @Volatile so reads in invokeProvider (outside the mutex) always observe the latest write
    // from registerProvider. Single-write-wins semantics are acceptable for the happy path.
    @Volatile private var provider: AuthTokenProvider? = null

    @Volatile private var cachedToken: ValidatedToken? = null

    // Reserved for MAGE-628 (concurrent-caller deduplication).
    @Suppress("unused")
    private var inFlightFetch: Deferred<String>? = null

    // Reserved for MAGE-630 (onTokenRefresh/offTokenRefresh observers). Matches the established
    // SDK observer-collection pattern (CopyOnWriteArrayList for thread-safe iteration while
    // observers add/remove themselves on arbitrary threads).
    @Suppress("unused")
    private val refreshObservers = CopyOnWriteArrayList<TokenRefreshObserver>()

    override fun registerProvider(provider: AuthTokenProvider) {
        cachedToken = null
        this.provider = provider
        Registry.log.info("AuthTokenProvider registered")
        scope.safeLaunch { tryEagerFetch() }
    }

    private suspend fun tryEagerFetch() {
        try {
            currentToken()
        } catch (e: CancellationException) {
            // Preserve structured concurrency by rethrowing cancellation.
            throw e
        } catch (_: Exception) {
            // The failure is already logged at ERROR by validateOrThrow (or surfaced by the
            // provider's own onFailure). Avoid double-logging at WARNING here.
            Registry.log.debug("Eager auth token fetch failed")
        }
    }

    override suspend fun currentToken(): ValidatedToken {
        val cached = cachedToken
        if (cached != null && isStillValid(cached)) {
            return cached
        }

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
