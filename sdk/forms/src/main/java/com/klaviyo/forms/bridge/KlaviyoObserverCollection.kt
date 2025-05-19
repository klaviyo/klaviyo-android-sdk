package com.klaviyo.forms.bridge

/**
 * TODO Manage all observers that inject data into the webview, to include
 *  - Profile identifiers
 *  - Lifecycle events
 *  - Analytics events
 *  - Host App navigation events
 */
internal class KlaviyoObserverCollection : ObserverCollection {
    override val observers: List<Observer> = emptyList()
}
