package com.klaviyo.forms.preview

import com.klaviyo.core.Registry
import com.klaviyo.core.forms.FormsPreview

/**
 * Holds the forms preview token in memory only. The token is a short-lived credential that makes
 * onsite-in-app-forms load draft forms instead of published forms. It is never persisted to disk
 * and must never be logged.
 */
internal class FormsPreviewTokenStore {

    companion object {
        /**
         * Returns [token] without surrounding whitespace if it is a well-formed preview token,
         * null if it is null or blank, and null with a warning otherwise. The token value itself
         * is never logged.
         */
        fun validate(token: String?): String? {
            val trimmed = token?.trim()
            return when {
                trimmed.isNullOrEmpty() -> null
                FormsPreview.isValidToken(trimmed) -> trimmed
                else -> null.also {
                    Registry.log.warning("Ignoring malformed forms preview token")
                }
            }
        }
    }

    /**
     * The current valid preview token, or null when forms should load normally
     */
    @Volatile
    var token: String? = null
        private set

    /**
     * Store [token] after validation. Null or blank clears the token, and a malformed token is
     * dropped with a warning, which also clears any previous token.
     *
     * @return True if the stored value changed
     */
    fun setToken(token: String?): Boolean {
        val validated = validate(token)
        val changed = synchronized(this) {
            (this.token != validated).also { this.token = validated }
        }

        if (changed) {
            Registry.log.debug(
                if (validated == null) "Forms preview token cleared" else "Forms preview token set"
            )
        }

        return changed
    }

    /**
     * Clear the stored token
     */
    fun clear() = setToken(null)
}
