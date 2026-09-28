package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.safeLaunch
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Delivers the auth token to the webview via [JsBridge.jwtMutation] at [NativeBridgeMessage.JsReady],
 * before [ProfileMutationObserver] injects profile identifiers at HandShook.
 *
 * The onsite personalization module only triggers the authenticated profile fetch when both a JWT
 * and profile identifiers are present, so the JWT must land first.
 *
 * Beyond the initial delivery, this observer subscribes to [AuthTokenManager.onTokenRefresh] so that
 * a token proactively refreshed while a form is displayed is re-injected into the webview, keeping
 * onsite from acting on a stale (eventually expired) JWT.
 *
 * It is also re-primed explicitly via [refreshForProfileChange], which [ProfileMutationObserver]
 * calls whenever the profile identity is replaced mid-session. `Klaviyo.setProfile`/the individual
 * identifier setters never touch [AuthTokenManager] — unlike `resetProfile`, they don't invalidate
 * the cached auth token — but onsite-personalization unconditionally drops its own copy of the JWT
 * whenever the profile identity changes (see onsite-personalization's `clearPersonalizationState`).
 * Without an explicit nudge here, nothing would refill it until the auth token's own unrelated
 * refresh schedule next happened to fire, leaving personalization broken for the new identity in
 * the meantime.
 */
internal class JwtObserver : JsBridgeObserver {

    /**
     * Completes once the JWT has been delivered. [ProfileMutationObserver] awaits this before
     * injecting profile identifiers. Reused while still pending so a re-entrant start does not
     * orphan a waiter that captured the previous reference.
     */
    @Volatile
    internal var jwtReady: CompletableDeferred<Unit> = CompletableDeferred()
        private set

    @Volatile private var stopped = false

    /**
     * Identity token for the current session, replaced on each [startObserver]. Both injection
     * paths capture it and re-check it once they reach the UI thread, so a callback queued in a
     * previous session (its fetch result, or a proactive-refresh echo) cannot inject into the fresh
     * webview a later session loaded. `@Volatile` because it is written off the UI thread by
     * [startObserver] and read on it.
     */
    @Volatile private var latestFetch: Any? = null

    private val scope = CoroutineScope(SupervisorJob() + Registry.dispatcher)
    private var fetchJob: Job? = null

    /**
     * Stable instance so it can be unregistered by reference via [AuthTokenManager.offTokenRefresh].
     * Invoked on the manager's IO dispatcher, so it hops to the UI thread before touching the bridge.
     */
    private val refreshObserver: TokenRefreshObserver = { jwt -> onTokenRefreshed(jwt) }

    /**
     * Monotonic sequence claimed by each token source when its injection is *requested*, not when it
     * resolves. The initial one-shot fetch reserves its slot up front in [startObserver]; the refresh
     * stream claims one each time it fires. Because the initial fetch takes its (lower) sequence
     * before it awaits, a proactive refresh that lands while the fetch is still in flight always
     * outranks it — so a slow or failed initial fetch can never clobber a fresher refreshed token.
     * Each injection applies only if it carries the highest sequence seen so far, so the newest token
     * always wins regardless of UI-callback ordering.
     */
    private val injectionSequence = AtomicLong(0L)

    /** Highest sequence applied to the webview. Only read/written on the UI thread. */
    private var lastInjectedSequence = 0L

    /**
     * Last token value injected into the *current* webview. Guards against re-injecting an
     * identical token within a session: a fast initial fetch that resolves within the interactive
     * budget delivers the same value both as its own result and via the
     * [AuthTokenManager.onTokenRefresh] echo that now fires on every acquisition. Null until the
     * first injection of a session, so an initial empty ("unauthenticated") token is still
     * delivered. Only read/written on the UI thread.
     */
    private var lastInjectedToken: String? = null

    /**
     * Set by [startObserver] so the next injection clears [lastInjectedToken] first. Each session
     * loads a fresh webview with no JWT, so the value-dedup must not carry over from a previous
     * session — otherwise a form reopened with an unchanged (still-valid) token would never receive
     * it. `@Volatile` because [startObserver] may run off the UI thread while injections run on it;
     * the flag is consumed on the UI thread so [lastInjectedToken] stays single-threaded.
     */
    @Volatile private var resetDedupOnNextInjection = false

    override fun startObserver() {
        stopped = false
        // A new session loads a fresh webview; forget the previous session's injected value so an
        // unchanged token is re-delivered rather than deduped away. Consumed on the UI thread.
        resetDedupOnNextInjection = true
        val thisFetch = Any()
        latestFetch = thisFetch
        // Reserve the initial fetch's place in the injection order now, at request time, so any
        // refresh that fires while the fetch is still in flight outranks it. Assigning the sequence
        // only once the token resolved let a slow or failed fetch clobber a fresher refreshed token.
        val fetchSequence = injectionSequence.incrementAndGet()
        val currentJwtReady = if (jwtReady.isCompleted) {
            CompletableDeferred<Unit>().also { jwtReady = it }
        } else {
            jwtReady
        }

        // off-then-on guarantees a single registration across re-entrant starts (duplicate
        // registrations would inject the refreshed token more than once).
        Registry.get<AuthTokenManager>().apply {
            offTokenRefresh(refreshObserver)
            onTokenRefresh(refreshObserver)
        }

        println("[MAGE1170_DEBUG] startObserver: fetchSequence=$fetchSequence")
        fetchJob?.cancel()
        fetchJob = scope.safeLaunch {
            val token = fetchTokenOrEmpty()
            println(
                "[MAGE1170_DEBUG] startObserver fetch resolved: " +
                    "tokenIsEmpty=${token.isEmpty()} tokenPrefix=${token.take(12)}"
            )

            Registry.threadHelper.runOnUiThread {
                if (latestFetch === thisFetch && !stopped) {
                    injectIfLatest(fetchSequence, token)
                    currentJwtReady.complete(Unit)
                } else {
                    println(
                        "[MAGE1170_DEBUG] startObserver fetch DROPPED " +
                            "(sameSession=${latestFetch === thisFetch}, stopped=$stopped)"
                    )
                }
            }
        }
    }

    override fun stopObserver() {
        stopped = true
        Registry.get<AuthTokenManager>().offTokenRefresh(refreshObserver)
        fetchJob?.cancel()
        fetchJob = null
    }

    /**
     * Fetches a fresh token when the profile identity is replaced mid-session — not the initial
     * identify, which [startObserver] already coordinates via [jwtReady]. See the class doc for why
     * this is needed: profile identity changes never touch [AuthTokenManager] on their own.
     *
     * Reuses the same injection-sequence protocol as the initial fetch and the refresh stream (see
     * [injectIfLatest]) — whichever of the three actually resolves newest wins, regardless of
     * completion order. Unlike those two, a successful fetch always re-injects even when the token
     * is unchanged from the last one injected: onsite-personalization dropped its own copy because
     * the *identity* changed, not because the token did, so a value-based dedup here would leave it
     * without a fresh push whenever the underlying auth token happens to still be valid.
     *
     * A failed fetch injects nothing at all, unlike [startObserver]'s empty-string fallback — this
     * call already reserved a sequence number, so force-injecting an empty result here would let a
     * merely-transient failure permanently block a legitimately good token that was already in
     * flight, or that arrives moments later via a different path with a lower sequence number.
     * There's no equivalent risk at the initial fetch: nothing has been injected into the fresh
     * webview yet for an empty result to wrongly outrank.
     *
     * No-ops silently if the observer has not been started (or has since been stopped) for the
     * current webview session — there is nothing to refresh into.
     */
    fun refreshForProfileChange() {
        val session = latestFetch
        println(
            "[MAGE1170_DEBUG] refreshForProfileChange called: " +
                "session=${session != null} stopped=$stopped"
        )
        if (session == null) return
        if (stopped) return
        val sequence = injectionSequence.incrementAndGet()
        println("[MAGE1170_DEBUG] refreshForProfileChange: sequence=$sequence")

        scope.safeLaunch {
            // Background budget, not the interactive one [startObserver] uses: nothing awaits this
            // result (unlike jwtReady), so there is no user-visible latency to protect — give the
            // provider more time to actually succeed rather than giving up early and injecting empty.
            val token = fetchTokenOrEmpty(AuthTokenManager.BACKGROUND_FETCH_TIMEOUT_MS)
            println(
                "[MAGE1170_DEBUG] refreshForProfileChange fetch resolved: " +
                    "tokenIsEmpty=${token.isEmpty()} tokenPrefix=${token.take(12)}"
            )
            if (token.isEmpty()) return@safeLaunch

            Registry.threadHelper.runOnUiThread {
                if (latestFetch === session && !stopped) {
                    injectIfLatest(sequence, token, forceReinject = true)
                } else {
                    println(
                        "[MAGE1170_DEBUG] refreshForProfileChange result DROPPED " +
                            "(sameSession=${latestFetch === session}, stopped=$stopped)"
                    )
                }
            }
        }
    }

    /** Fetches the current token via [AuthTokenManager], or "" (with a log) on failure. */
    private suspend fun fetchTokenOrEmpty(
        timeoutMs: Long = AuthTokenManager.INTERACTIVE_FETCH_TIMEOUT_MS
    ): String = try {
        Registry.get<AuthTokenManager>().currentToken(timeoutMs).rawToken
    } catch (e: CancellationException) {
        throw e
    } catch (_: AuthTokenException.NoProviderRegistered) {
        Registry.log.debug("Auth not enabled — injecting empty JWT")
        ""
    } catch (_: Exception) {
        Registry.log.warning("Auth token fetch failed — injecting empty JWT")
        ""
    }

    /**
     * Re-inject a proactively-refreshed token into the webview. The manager only notifies on a
     * successful fetch, so [jwt] is always a real (non-empty) token here. Captures the current
     * [latestFetch] and re-checks it (plus [stopped]) on the UI thread: a refresh dispatched during
     * a previous session could otherwise run after a stop/start cycle flipped [stopped] back to
     * false and inject a stale token into the freshly loaded webview.
     */
    private fun onTokenRefreshed(jwt: String) {
        val sequence = injectionSequence.incrementAndGet()
        val session = latestFetch
        println(
            "[MAGE1170_DEBUG] onTokenRefreshed: sequence=$sequence tokenPrefix=${jwt.take(12)}"
        )
        Registry.threadHelper.runOnUiThread {
            if (!stopped && latestFetch === session) {
                injectIfLatest(sequence, jwt)
            }
        }
    }

    /**
     * Inject [token] only if [sequence] is newer than any already applied, so an out-of-order
     * initial-fetch callback cannot overwrite a fresher token delivered via the refresh stream.
     * Within a session, skips the bridge call when the value is unchanged from the last injection,
     * so a token delivered twice (e.g. a fast initial fetch plus its refresh-stream echo) hits the
     * webview once; a new session first clears that baseline (see [resetDedupOnNextInjection]) so a
     * reopened form still receives an unchanged token. Must be called on the UI thread, where
     * [lastInjectedSequence] and [lastInjectedToken] are exclusively accessed.
     *
     * [forceReinject] bypasses the value-based dedup (the sequence-ordering check still applies).
     * [refreshForProfileChange] passes this because the webview dropped its own copy of the JWT
     * when the profile changed, independent of whether the token value itself changed.
     */
    private fun injectIfLatest(sequence: Long, token: String, forceReinject: Boolean = false) {
        if (resetDedupOnNextInjection) {
            resetDedupOnNextInjection = false
            lastInjectedToken = null
        }
        println(
            "[MAGE1170_DEBUG] injectIfLatest: sequence=$sequence lastInjectedSequence=" +
                "$lastInjectedSequence forceReinject=$forceReinject " +
                "tokenPrefix=${token.take(12)} lastInjectedTokenPrefix=${lastInjectedToken?.take(12)}"
        )
        if (sequence > lastInjectedSequence) {
            lastInjectedSequence = sequence
            if (forceReinject || token != lastInjectedToken) {
                lastInjectedToken = token
                println("[MAGE1170_DEBUG] injectIfLatest: CALLING jwtMutation")
                Registry.get<JsBridge>().jwtMutation(token)
            } else {
                println("[MAGE1170_DEBUG] injectIfLatest: deduped (same value)")
            }
        } else {
            println("[MAGE1170_DEBUG] injectIfLatest: STALE sequence, dropped")
        }
    }
}
