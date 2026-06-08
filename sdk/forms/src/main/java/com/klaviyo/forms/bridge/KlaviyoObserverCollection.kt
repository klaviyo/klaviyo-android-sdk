package com.klaviyo.forms.bridge

/**
 * Manages the collection of observers that inject data into the webview
 */
internal class KlaviyoObserverCollection : JsBridgeObserverCollection {
    // JwtObserver owns its jwtReady deferred and creates a fresh one on each startObserver call.
    // ProfileMutationObserver reads jwtReady from this reference at its own startObserver time,
    // so it always awaits the deferred for the current WebView session rather than a stale one.
    private val jwtObserver = JwtObserver()

    override val observers: List<JsBridgeObserver> by lazy {
        listOf(
            CompanyObserver(),
            LifecycleObserver(),
            jwtObserver,
            ProfileMutationObserver(jwtObserver),
            ProfileEventObserver()
        )
    }
}
