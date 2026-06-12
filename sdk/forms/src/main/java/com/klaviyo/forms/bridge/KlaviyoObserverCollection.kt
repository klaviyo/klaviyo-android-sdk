package com.klaviyo.forms.bridge

/**
 * Manages the collection of observers that inject data into the webview
 */
internal class KlaviyoObserverCollection : JsBridgeObserverCollection {
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
