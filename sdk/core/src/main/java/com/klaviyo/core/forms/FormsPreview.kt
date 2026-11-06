package com.klaviyo.core.forms

import com.klaviyo.core.Constants
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared rules for the in-app forms draft-preview token: the key name, token format and URL
 * parsing.
 */
object FormsPreview {
    /**
     * Key carrying a preview token: a deep link query parameter, and a push key/value pair
     */
    const val PREVIEW_TOKEN_KEY = Constants.FORMS_PREVIEW_KEY

    private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")

    /**
     * True if [token] is a well-formed preview token. Does not trim.
     */
    fun isValidToken(token: String?): Boolean = token != null && TOKEN_PATTERN.matches(token)

    /**
     * The raw [PREVIEW_TOKEN_KEY] query parameter value of [uri], or null if the URI has no such
     * parameter. Works for any scheme, including custom schemes and http(s) links, with any host
     * and path. The value is not validated; see [isValidToken].
     */
    fun previewTokenParameter(uri: String?): String? {
        val query = uri?.substringBefore('#')?.substringAfter('?', "")?.takeIf { it.isNotEmpty() }
            ?: return null
        return query.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it[0] == PREVIEW_TOKEN_KEY }
            ?.let { pair -> pair.getOrNull(1)?.let(::decode) ?: "" }
    }

    /**
     * [uri] with every [PREVIEW_TOKEN_KEY] query parameter removed. The query is the part after
     * the first `?` and before any `#`, as in [previewTokenParameter]; everything else is kept
     * byte for byte. Returns [uri] unchanged when it has no such parameter.
     */
    fun withoutPreviewTokenParameter(uri: String): String {
        val fragmentStart = uri.indexOf('#').takeIf { it >= 0 } ?: uri.length
        val queryStart = uri.indexOf('?').takeIf { it in 0 until fragmentStart } ?: return uri
        val params = uri.substring(queryStart + 1, fragmentStart).split('&')
        val kept = params.filterNot { it.substringBefore('=') == PREVIEW_TOKEN_KEY }
        if (kept.size == params.size) return uri
        val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        return uri.substring(0, queryStart) + query + uri.substring(fragmentStart)
    }

    /**
     * The [PREVIEW_TOKEN_KEY] string value in a push's key/value pairs [json] string, or null if
     * the string is absent, not a JSON object, or has no such key, or the value is not a string.
     * The value is not validated.
     */
    fun previewTokenFromKeyValuePairs(json: String?): String? =
        json?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.opt(PREVIEW_TOKEN_KEY) as? String

    /**
     * A push's key/value pairs [json] string with [PREVIEW_TOKEN_KEY] removed, or [json]
     * unchanged when it has no such key or is not a JSON object.
     */
    fun keyValuePairsWithoutPreviewToken(json: String): String {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return json
        if (!obj.has(PREVIEW_TOKEN_KEY)) return json
        obj.remove(PREVIEW_TOKEN_KEY)
        return obj.toString()
    }

    /**
     * A push's action buttons [json] array string with [PREVIEW_TOKEN_KEY] removed from each
     * button's `url`, as in [withoutPreviewTokenParameter], or [json] unchanged when no button
     * `url` has the parameter or it is not a JSON array.
     */
    fun actionButtonsWithoutPreviewToken(json: String): String {
        val buttons = runCatching { JSONArray(json) }.getOrNull() ?: return json
        var changed = false
        for (i in 0 until buttons.length()) {
            val button = buttons.optJSONObject(i) ?: continue
            val url = button.opt(Constants.URL_PARAMETER) as? String ?: continue
            val stripped = withoutPreviewTokenParameter(url)
            if (stripped != url) {
                button.put(Constants.URL_PARAMETER, stripped)
                changed = true
            }
        }
        return if (changed) buttons.toString() else json
    }

    private fun decode(value: String): String? = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull()
}
