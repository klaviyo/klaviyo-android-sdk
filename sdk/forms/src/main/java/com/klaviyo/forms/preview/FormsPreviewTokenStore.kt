package com.klaviyo.forms.preview

import com.klaviyo.core.Registry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Observer notified when the stored forms preview token changes. May be invoked on any thread.
 */
internal typealias FormsPreviewTokenObserver = () -> Unit

/**
 * Holds the forms preview token in memory only. The token is a short-lived credential that makes
 * onsite-in-app-forms load draft forms instead of published forms. It is never persisted to disk
 * and must never be logged.
 */
internal class FormsPreviewTokenStore {

    companion object {
        /**
         * Format the onsite-in-app-forms package accepts for a preview token
         */
        private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")

        /**
         * Returns [token] without surrounding whitespace if it is a well-formed preview token,
         * null if it is null or blank, and null with a warning otherwise. The token value itself
         * is never logged.
         */
        fun validate(token: String?): String? {
            val trimmed = token?.trim()
            return when {
                trimmed.isNullOrEmpty() -> null
                TOKEN_PATTERN.matches(trimmed) -> trimmed
                else -> null.also {
                    Registry.log.warning("Ignoring malformed forms preview token")
                }
            }
        }
    }

    private val observers = CopyOnWriteArrayList<FormsPreviewTokenObserver>()

    /**
     * The current valid preview token, or null when forms should load normally
     */
    @Volatile
    var token: String? = null
        private set

    /**
     * The token last rendered into the InAppFormsTemplate head, so the bridge can tell whether
     * the webview's copy went stale before the JS bridge was ready. Null if none was rendered.
     */
    @Volatile
    var renderedToken: String? = null
        private set

    /**
     * Read the current token for rendering into a new webview template and record it as
     * [renderedToken]
     */
    fun tokenForTemplate(): String? = token.also { renderedToken = it }

    /**
     * Store [token] after validation. Null or blank clears the token, and a malformed token is
     * dropped with a warning, which also clears any previous token. Observers are notified only
     * when the stored value changes.
     */
    fun setToken(token: String?) {
        val validated = validate(token)
        val changed = synchronized(this) {
            (this.token != validated).also { this.token = validated }
        }

        if (changed) {
            Registry.log.debug(
                if (validated == null) "Forms preview token cleared" else "Forms preview token set"
            )
            observers.forEach { it() }
        }
    }

    /**
     * Clear the stored token
     */
    fun clear() = setToken(null)

    fun onTokenChange(observer: FormsPreviewTokenObserver) {
        observers += observer
    }

    fun offTokenChange(observer: FormsPreviewTokenObserver) {
        observers -= observer
    }
}
