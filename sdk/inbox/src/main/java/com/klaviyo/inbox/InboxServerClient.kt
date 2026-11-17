package com.klaviyo.inbox

import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.InboxApiResponse
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** A state change to send, with the time the user made it */
internal data class InboxServerChange(
    val transmissionId: String,
    val change: InboxStateChange,
    val changedAtMs: Long
) {
    override fun toString(): String = "InboxServerChange(change=$change, changedAtMs=$changedAtMs)"
}

/**
 * The Mobile Inbox server calls. Each call is one authenticated request, with one retry after a
 * token refresh. It reads and writes nothing on the device.
 */
internal interface InboxServerClient {

    /**
     * Fetch one page of the profile's inbox.
     *
     * @param profileId The profile to read, null or blank gives [InboxApiResult.NotReady]
     * @param cursor The raw cursor from the previous page's [InboxServerPage.nextCursor], or null
     *   for the first page
     * @param pageSize 1 to 100
     */
    suspend fun fetchPage(
        profileId: String?,
        cursor: String? = null,
        pageSize: Int = InboxQuery.MAX_PAGE_SIZE
    ): InboxApiResult<InboxServerPage>

    /**
     * Send 1 to 100 state changes. A success is a receipt that the server got them, not proof they
     * were applied. Success holds the number accepted, which always equals the number sent.
     */
    suspend fun submitChanges(
        profileId: String?,
        changes: List<InboxServerChange>
    ): InboxApiResult<Int>
}

/**
 * @param apiClient The transport, or null to use the registered one
 */
internal class KlaviyoInboxServerClient(
    private val apiClient: ApiClient? = null
) : InboxServerClient {

    private companion object {
        const val FETCH = "fetch"
        const val SUBMIT = "submit"
        const val MIN_CHANGES = 1
        const val MAX_CHANGES = 100
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        val REDIRECT = 300..399
        val CLIENT_ERROR = 400..499
        val SERVER_ERROR = 500..599

        const val STATE_CHANGE_TYPE = "inbox-message-state-change"
        const val DATA = "data"
        const val TYPE = "type"
        const val ATTRIBUTES = "attributes"
        const val PROFILE_ID = "profile_id"
        const val CHANGES = "changes"
        const val TRANSMISSION_ID = "transmission_id"
        const val CHANGE = "change"
        const val CHANGED_AT = "changed_at"
    }

    private val api: ApiClient? get() = apiClient ?: Registry.getOrNull<ApiClient>()

    /** Ends a call early with [result]. Control flow only, so no stack trace is captured */
    private class Stop(val result: InboxApiResult<Nothing>) : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    override suspend fun fetchPage(
        profileId: String?,
        cursor: String?,
        pageSize: Int
    ): InboxApiResult<InboxServerPage> {
        if (pageSize !in InboxQuery.MIN_PAGE_SIZE..InboxQuery.MAX_PAGE_SIZE) {
            return rejectedLocally(FETCH, "page size out of range")
        }
        return execute(
            operation = FETCH,
            profileId = profileId,
            send = { api, token, id -> api.fetchInboxPage(token, id, cursor, pageSize) },
            parse = { InboxServerParser.parsePage(it.body, cursor) }
        )
    }

    override suspend fun submitChanges(
        profileId: String?,
        changes: List<InboxServerChange>
    ): InboxApiResult<Int> {
        if (changes.size !in MIN_CHANGES..MAX_CHANGES) {
            return rejectedLocally(SUBMIT, "change count out of range")
        }
        if (changes.any { it.transmissionId.isBlank() }) {
            return rejectedLocally(SUBMIT, "blank transmission id")
        }
        return execute(
            operation = SUBMIT,
            profileId = profileId,
            send = { api, token, id -> api.submitInboxStateChanges(token, body(id, changes)) },
            parse = { InboxServerParser.parseAccepted(it.body, changes.size) }
        )
    }

    private fun rejectedLocally(operation: String, detail: String): InboxApiResult.Rejected =
        InboxApiResult.Rejected(null, detail).also { log(operation, it) }

    /**
     * Auth flow shared by both calls.
     *
     * The 401 handling is check-then-refresh, which is not atomic. Two calls can both see the same
     * rejected token and both refresh, costing one extra provider fetch.
     */
    private suspend fun <T> execute(
        operation: String,
        profileId: String?,
        send: suspend (api: ApiClient, token: String, profileId: String) -> InboxApiResponse,
        parse: (InboxApiResponse) -> InboxApiResult<T>
    ): InboxApiResult<T> {
        val result = try {
            val manager = Registry.getOrNull<AuthTokenManager>() ?: throw Stop(
                InboxApiResult.NotReady
            )
            val api = api ?: throw Stop(InboxApiResult.NotReady)
            val id = profileId?.takeIf { it.isNotBlank() } ?: throw Stop(InboxApiResult.NotReady)
            val generation = manager.profileGeneration()

            var token = token(manager)
            ensureSameProfile(manager, generation)
            var response = send(api, token, id)

            if (response.code == HTTP_UNAUTHORIZED) {
                ensureSameProfile(manager, generation)
                if (manager.isCurrentToken(token)) manager.refreshRejectedToken()
                token = token(manager)
                ensureSameProfile(manager, generation)
                response = send(api, token, id)
            }

            Registry.log.verbose("Mobile Inbox $operation: ${response.code ?: "no response"}")
            classify(response, parse).also { ensureSameProfile(manager, generation) }
        } catch (stop: Stop) {
            stop.result
        }
        return result.also { log(operation, it) }
    }

    private suspend fun token(manager: AuthTokenManager): String = try {
        manager.currentToken().rawToken
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthTokenException.NoProviderRegistered) {
        throw Stop(InboxApiResult.NotReady)
    } catch (e: AuthTokenException.NotIdentified) {
        throw Stop(InboxApiResult.NotReady)
    } catch (e: Exception) {
        // The exception is dropped on purpose: a provider's message could echo the token
        throw Stop(InboxApiResult.Unavailable(UnavailableReason.TOKEN, null, null))
    }

    private fun ensureSameProfile(manager: AuthTokenManager, generation: Long) {
        if (manager.profileGeneration() != generation) throw Stop(InboxApiResult.IdentityChanged)
    }

    private fun <T> classify(
        response: InboxApiResponse,
        parse: (InboxApiResponse) -> InboxApiResult<T>
    ): InboxApiResult<T> {
        val code = response.code
        return when {
            !response.sent -> unavailable(UnavailableReason.OFFLINE, null)
            code == null -> unavailable(UnavailableReason.IO, null)
            code == HTTP_OK -> parse(response)
            code == HTTP_UNAUTHORIZED -> InboxApiResult.Unauthorized
            code == HTTP_FORBIDDEN -> InboxServerParser.errorDetail(response.body)
                ?.let { InboxApiResult.Forbidden(it) }
                ?: unavailable(UnavailableReason.EDGE_BLOCK, code)
            code == HTTP_TOO_MANY_REQUESTS ->
                unavailable(UnavailableReason.RATE_LIMITED, code, response.retryAfterMs)
            code in SERVER_ERROR -> unavailable(
                UnavailableReason.SERVER,
                code,
                response.retryAfterMs
            )
            code in REDIRECT -> unavailable(UnavailableReason.EDGE_BLOCK, code)
            code in CLIENT_ERROR ->
                InboxApiResult.Rejected(code, InboxServerParser.errorDetail(response.body))
            else -> InboxApiResult.ProtocolFailure("unexpected status $code")
        }
    }

    private fun unavailable(reason: UnavailableReason, code: Int?, retryAfterMs: Long? = null) =
        InboxApiResult.Unavailable(reason, code, retryAfterMs)

    /**
     * One log call per outcome. Messages hold the operation, a status and fixed text. Server
     * detail, the token, the profile ID and content stay out of the log.
     */
    private fun log(operation: String, result: InboxApiResult<*>) {
        val prefix = "Mobile Inbox $operation"
        when (result) {
            is InboxApiResult.Success -> Unit
            InboxApiResult.NotReady -> Registry.log.debug("$prefix is not ready")
            InboxApiResult.IdentityChanged -> Registry.log.debug("$prefix dropped: profile changed")
            InboxApiResult.Unauthorized -> Registry.log.warning(
                "$prefix unauthorized after refresh"
            )
            is InboxApiResult.Unavailable -> Registry.log.warning(
                "$prefix unavailable: ${result.reason}" + (result.code?.let { ", status $it" } ?: "")
            )
            is InboxApiResult.Forbidden -> Registry.log.error("$prefix forbidden")
            is InboxApiResult.Rejected -> Registry.log.error(
                when (result.code) {
                    // Local validation, the detail is fixed text from this class
                    null -> "$prefix rejected: ${result.detail}"
                    HTTP_NOT_FOUND -> "$prefix rejected: status 404, check the API revision or route"
                    else -> "$prefix rejected: status ${result.code}"
                }
            )
            is InboxApiResult.ProtocolFailure -> Registry.log.error(
                "$prefix got an unexpected response: ${result.reason}"
            )
        }
    }

    private fun body(profileId: String, changes: List<InboxServerChange>): String {
        val items = JSONArray()
        changes.forEach {
            items.put(
                JSONObject()
                    .put(TRANSMISSION_ID, it.transmissionId)
                    .put(CHANGE, it.change.wireName())
                    .put(CHANGED_AT, InboxServerTime.format(it.changedAtMs))
            )
        }
        val attributes = JSONObject().put(PROFILE_ID, profileId).put(CHANGES, items)
        return JSONObject()
            .put(DATA, JSONObject().put(TYPE, STATE_CHANGE_TYPE).put(ATTRIBUTES, attributes))
            .toString()
    }

    private fun InboxStateChange.wireName(): String = when (this) {
        InboxStateChange.MARK_READ -> "mark_read"
        InboxStateChange.MARK_UNREAD -> "mark_unread"
        InboxStateChange.ARCHIVE -> "archive"
        InboxStateChange.UNARCHIVE -> "unarchive"
    }
}
