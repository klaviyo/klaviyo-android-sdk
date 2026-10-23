package com.klaviyo.core.forms

/**
 * Receives an in-app forms draft-preview token found outside the forms module, for example in an
 * opened push. Implemented in `forms` and resolved lazily via [com.klaviyo.core.Registry]; when
 * that module is absent the lookup is null, so the token is dropped.
 */
fun interface FormsPreviewTokenSink {
    /**
     * Start a forms preview with [token], or clear the preview with null. Must not throw.
     */
    fun setPreviewToken(token: String?)
}
