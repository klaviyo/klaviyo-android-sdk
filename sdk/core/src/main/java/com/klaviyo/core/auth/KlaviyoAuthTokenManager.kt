package com.klaviyo.core.auth

import com.klaviyo.core.Registry
import com.klaviyo.core.safeLaunch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class KlaviyoAuthTokenManager : AuthTokenManager {

    override val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Registry.dispatcher)

    private val mutex = Mutex()

    private var provider: AuthTokenProvider? = null
    private var cachedToken: ValidatedToken? = null

    // Reserved for MAGE-628 (concurrent-caller deduplication).
    @Suppress("unused")
    private var inFlightFetch: Deferred<String>? = null

    // Reserved for MAGE-629 (proactive refresh scheduling).
    @Suppress("unused")
    private var refreshJob: Job? = null

    // Reserved for MAGE-630 (onTokenRefresh/offTokenRefresh observers); typed as a function for
    // now — the formal observer interface will be introduced alongside the public API.
    @Suppress("unused")
    private val refreshObservers = mutableListOf<(String) -> Unit>()

    override suspend fun registerProvider(provider: AuthTokenProvider) {
        mutex.withLock {
            this.provider = provider
            this.cachedToken = null
        }
        Registry.log.info("AuthTokenProvider registered")

        coroutineScope.safeLaunch {
            try {
                currentToken()
            } catch (e: CancellationException) {
                // Preserve structured concurrency by rethrowing cancellation.
                throw e
            } catch (e: Exception) {
                Registry.log.warning("Eager auth token fetch failed", e)
            }
        }
    }

    override suspend fun currentToken(): String {
        val cached = mutex.withLock { cachedToken }
        if (cached != null && isStillValid(cached)) {
            return cached.rawToken
        }

        val jwt = invokeProvider()
        val token = validateOrThrow(jwt)

        mutex.withLock { cachedToken = token }
        Registry.log.info(
            "Auth token acquired (exp=${token.expiresAtEpochSeconds}, iat=${token.issuedAtEpochSeconds})"
        )
        return token.rawToken
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
            IllegalStateException("No auth token provider registered")
        )
    }

    private fun validateOrThrow(jwt: String): ValidatedToken =
        when (val result = JWTParser.parseAndValidate(jwt)) {
            is JWTValidationResult.Valid -> result.token
            else -> {
                val reason = result::class.simpleName ?: "Unknown"
                val error = IllegalStateException("Auth token validation failed: $reason")
                Registry.log.error("Auth token validation failed: $reason", error)
                throw error
            }
        }

    private fun isStillValid(token: ValidatedToken): Boolean {
        val now = Registry.clock.currentTimeMillis() / 1000L
        return now < token.expiresAtEpochSeconds - JWTParser.DEFAULT_LEEWAY_SECONDS
    }
}
