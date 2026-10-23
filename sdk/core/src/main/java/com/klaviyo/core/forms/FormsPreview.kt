package com.klaviyo.core.forms

import java.net.URI
import java.net.URLDecoder

/**
 * Shared rules for the in-app forms draft-preview token: the key name, token format and URL
 * parsing.
 */
object FormsPreview {
    /**
     * Key carrying a preview token: a deep link query parameter, and a push key/value pair
     */
    const val PREVIEW_TOKEN_KEY = "klaviyo-forms-preview"

    private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")

    /**
     * True if [token] is a well-formed preview token. Does not trim.
     */
    fun isValidToken(token: String?): Boolean = token != null && TOKEN_PATTERN.matches(token)

    /**
     * The raw [PREVIEW_TOKEN_KEY] query parameter value of [uri], or null if the URI has no such
     * parameter or cannot be parsed. Works for custom schemes and http(s) links, with any host
     * and path. The value is not validated; see [isValidToken].
     */
    fun previewTokenParameter(uri: String?): String? {
        val query = uri?.let { runCatching { URI(it).rawQuery }.getOrNull() } ?: return null
        return query.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it[0] == PREVIEW_TOKEN_KEY }
            ?.let { pair -> pair.getOrNull(1)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() } ?: "" }
    }
}
