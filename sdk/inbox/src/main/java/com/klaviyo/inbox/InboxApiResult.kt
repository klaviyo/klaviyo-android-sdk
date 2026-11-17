package com.klaviyo.inbox

/**
 * Outcome of one Mobile Inbox server call. No result carries a token or profile ID, and the
 * [toString] of each omits message content, cursors and transmission IDs. The `detail` of
 * [Forbidden] and [Rejected] is server text that could echo request values, so do not log it.
 */
internal sealed class InboxApiResult<out T> {

    /** The call succeeded with a well-formed response */
    data class Success<out T>(val value: T) : InboxApiResult<T>()

    /** No auth manager, no identified profile, no token provider or no profile ID yet. Nothing was sent */
    data object NotReady : InboxApiResult<Nothing>()

    /**
     * The server rejected the token, again after one refresh and retry. Logged as a warning, not an
     * error, because a later call with a fresh token can still succeed.
     */
    data object Unauthorized : InboxApiResult<Nothing>()

    /** A structured 403 from the API: missing scope, denied access or a token that does not match the profile */
    data class Forbidden(val detail: String?) : InboxApiResult<Nothing>() {
        override fun toString(): String = "Forbidden"
    }

    /**
     * The request will not succeed as sent. [code] is null when it failed local validation.
     * Every other 4xx lands here, including 408 and 425, which the API does not document.
     */
    data class Rejected(val code: Int?, val detail: String?) : InboxApiResult<Nothing>() {
        override fun toString(): String = "Rejected(code=$code)"
    }

    /** Retryable. [retryAfterMs] is the server's requested delay, when it sent one */
    data class Unavailable(
        val reason: UnavailableReason,
        val code: Int?,
        val retryAfterMs: Long?
    ) : InboxApiResult<Nothing>()

    /**
     * The server answered outside the documented contract: a malformed 200, or a non-error status
     * other than 200 such as 204. Never an empty page. For a submit, [sent] and [accepted] are the
     * change counts, so the caller keeps its changes.
     */
    data class ProtocolFailure(
        val reason: String,
        val sent: Int? = null,
        val accepted: Long? = null
    ) : InboxApiResult<Nothing>()

    /** The profile changed during the call, so the response is not for the current profile */
    data object IdentityChanged : InboxApiResult<Nothing>()
}

/** Why a call is worth retrying later */
internal enum class UnavailableReason {
    TOKEN,
    EDGE_BLOCK,
    RATE_LIMITED,
    SERVER,
    OFFLINE,
    IO
}
