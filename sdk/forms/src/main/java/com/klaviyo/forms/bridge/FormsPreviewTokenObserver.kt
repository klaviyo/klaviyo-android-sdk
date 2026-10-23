package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.forms.preview.FormsPreviewTokenObserver as TokenChangeObserver
import com.klaviyo.forms.preview.FormsPreviewTokenStore

/**
 * Delivers forms preview token changes to the webview via [JsBridge.formsPreviewTokenMutation].
 * The token present when the webview was created is seeded into the template head, so on start
 * this only pushes the token if it changed after the template was rendered.
 */
internal class FormsPreviewTokenObserver : JsBridgeObserver {

    private val store: FormsPreviewTokenStore
        get() = Registry.get<FormsPreviewTokenStore>()

    /**
     * Stable instance so it can be unregistered by reference
     */
    private val tokenObserver: TokenChangeObserver = { deliverToken() }

    override fun startObserver() {
        store.onTokenChange(tokenObserver)
        if (store.token != store.renderedToken) deliverToken()
    }

    override fun stopObserver() = store.offTokenChange(tokenObserver)

    /**
     * An empty value tells onsite-in-app-forms to end preview and load published forms
     */
    private fun deliverToken() = Registry.get<JsBridge>().formsPreviewTokenMutation(
        store.token.orEmpty()
    )
}
