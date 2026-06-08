package com.klaviyo.forms.bridge

import kotlinx.coroutines.CompletableDeferred

/**
 * Manages the collection of observers that inject data into the webview
 */
internal class KlaviyoObserverCollection : JsBridgeObserverCollection {
    // Shared deferred that JwtObserver completes (or cancels) after delivering the JWT.
    // ProfileMutationObserver awaits it before its initial profile injection, ensuring the
    // JWT attribute is set before profile identifiers arrive in the onsite JS module.
    private val jwtReady = CompletableDeferred<Unit>()

    override val observers: List<JsBridgeObserver> by lazy {
        listOf(
            CompanyObserver(),
            LifecycleObserver(),
            JwtObserver(jwtReady),
            ProfileMutationObserver(jwtReady),
            ProfileEventObserver()
        )
    }
}
