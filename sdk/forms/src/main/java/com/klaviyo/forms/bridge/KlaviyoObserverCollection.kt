package com.klaviyo.forms.bridge

/**
 * Manages the collection of observers that inject data into the webview
 */
internal class KlaviyoObserverCollection : JsBridgeObserverCollection {
    // JwtObserver owns its jwtReady deferred. ProfileMutationObserver reads jwtReady from this
    // reference at its own startObserver time, so it always awaits whichever deferred is current
    // for the active WebView session rather than one captured at construction.
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
