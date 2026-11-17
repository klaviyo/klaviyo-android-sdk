package com.klaviyo.inbox

import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.InboxApiResponse
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.inbox.InboxServerFixtures.CLOUDFLARE_403
import com.klaviyo.inbox.InboxServerFixtures.CURSOR
import com.klaviyo.inbox.InboxServerFixtures.PROFILE_ID
import com.klaviyo.inbox.InboxServerFixtures.STRUCTURED_400
import com.klaviyo.inbox.InboxServerFixtures.STRUCTURED_403
import com.klaviyo.inbox.InboxServerFixtures.TRANSMISSION_ID
import com.klaviyo.inbox.InboxServerFixtures.nextLink
import com.klaviyo.inbox.InboxServerFixtures.ok
import com.klaviyo.inbox.InboxServerFixtures.page
import com.klaviyo.inbox.InboxServerFixtures.response
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class KlaviyoInboxServerClientTest : BaseTest() {

    private val api = mockk<ApiClient>()
    private val auth = mockk<AuthTokenManager>()
    private val client = KlaviyoInboxServerClient(api)

    private var generation = 1L

    private fun token(raw: String) = ValidatedToken(
        raw,
        expiresAtEpochSeconds = 2,
        issuedAtEpochSeconds = 1
    )

    @Before
    override fun setup() {
        super.setup()
        Registry.register<AuthTokenManager>(auth)
        every { auth.profileGeneration() } answers { generation }
        coEvery { auth.currentToken(any()) } returns token(TOKEN)
        every { auth.isCurrentToken(any()) } returns true
        every { auth.refreshRejectedToken() } returns Unit
        coEvery { api.fetchInboxPage(any(), any(), any(), any()) } returns ok(page())
        coEvery { api.submitInboxStateChanges(any(), any()) } returns ok(
            InboxServerFixtures.acceptedBody(1)
        )
    }

    @After
    override fun cleanup() {
        Registry.unregister<AuthTokenManager>()
        super.cleanup()
    }

    private fun fetch(
        profileId: String? = PROFILE_ID,
        cursor: String? = null,
        pageSize: Int = 100
    ) = runBlocking { client.fetchPage(profileId, cursor, pageSize) }

    private fun submit(
        changes: List<InboxServerChange> = listOf(change()),
        profileId: String? = PROFILE_ID
    ) = runBlocking { client.submitChanges(profileId, changes) }

    private fun change(
        id: String = TRANSMISSION_ID,
        change: InboxStateChange = InboxStateChange.MARK_READ,
        at: Long = CHANGED_AT_MS
    ) = InboxServerChange(id, change, at)

    private fun onFetch(vararg responses: InboxApiResponse) {
        coEvery { api.fetchInboxPage(any(), any(), any(), any()) } returnsMany responses.toList()
    }

    private fun assertNoHttp() {
        coVerify(exactly = 0) { api.fetchInboxPage(any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.submitInboxStateChanges(any(), any()) }
    }

    private fun assertUnavailable(
        reason: UnavailableReason,
        code: Int?,
        retryAfterMs: Long? = null,
        result: InboxApiResult<*>
    ) = assertEquals(InboxApiResult.Unavailable(reason, code, retryAfterMs), result)

    // region not ready

    @Test
    fun `no auth manager is not ready and sends nothing`() {
        Registry.unregister<AuthTokenManager>()

        assertEquals(InboxApiResult.NotReady, fetch())
        assertEquals(InboxApiResult.NotReady, submit())
        assertNoHttp()
    }

    @Test
    fun `no registered transport is not ready`() {
        val unwired = KlaviyoInboxServerClient()

        assertEquals(InboxApiResult.NotReady, runBlocking { unwired.fetchPage(PROFILE_ID) })
        assertEquals(
            InboxApiResult.NotReady,
            runBlocking { unwired.submitChanges(PROFILE_ID, listOf(change())) }
        )
    }

    @Test
    fun `the default constructor uses the registered transport`() {
        Registry.register<ApiClient>(api)
        try {
            val wired = KlaviyoInboxServerClient()

            assertTrue(runBlocking { wired.fetchPage(PROFILE_ID) } is InboxApiResult.Success)
            coVerify(exactly = 1) { api.fetchInboxPage(TOKEN, PROFILE_ID, null, 100) }
        } finally {
            Registry.unregister<ApiClient>()
        }
    }

    @Test
    fun `a null or blank profile id is not ready and sends nothing`() {
        listOf(null, "", "   ").forEach {
            assertEquals(InboxApiResult.NotReady, fetch(profileId = it))
            assertEquals(InboxApiResult.NotReady, submit(profileId = it))
        }
        assertNoHttp()
        coVerify(exactly = 0) { auth.currentToken(any()) }
    }

    @Test
    fun `no provider and not identified are not ready and send nothing`() {
        listOf(AuthTokenException.NoProviderRegistered, AuthTokenException.NotIdentified).forEach {
            coEvery { auth.currentToken(any()) } throws it

            assertEquals(InboxApiResult.NotReady, fetch())
            assertEquals(InboxApiResult.NotReady, submit())
        }
        assertNoHttp()
    }

    @Test
    fun `other token failures are unavailable for the token`() {
        listOf(
            AuthTokenException.TimedOut,
            AuthTokenException.ValidationFailed("expired"),
            AuthTokenException.ManagerStopped,
            AuthTokenException.ProviderCancelled(IOException("x")),
            IllegalStateException("provider blew up")
        ).forEach {
            coEvery { auth.currentToken(any()) } throws it

            assertUnavailable(UnavailableReason.TOKEN, null, result = fetch())
            assertUnavailable(UnavailableReason.TOKEN, null, result = submit())
        }
        assertNoHttp()
    }

    @Test
    fun `cancellation while waiting for a token is rethrown`() {
        coEvery { auth.currentToken(any()) } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) { fetch() }
        assertNoHttp()
    }

    // endregion

    // region request

    @Test
    fun `fetch sends the token, profile, cursor and page size`() {
        fetch(cursor = CURSOR, pageSize = 25)

        coVerify(exactly = 1) { api.fetchInboxPage(TOKEN, PROFILE_ID, CURSOR, 25) }
    }

    @Test
    fun `fetch defaults to the first page of 100`() {
        runBlocking { client.fetchPage(PROFILE_ID) }

        coVerify { api.fetchInboxPage(TOKEN, PROFILE_ID, null, 100) }
    }

    @Test
    fun `a page size outside 1 to 100 is rejected locally`() {
        listOf(0, -1, 101, Int.MAX_VALUE).forEach {
            assertEquals(
                InboxApiResult.Rejected(null, "page size out of range"),
                fetch(pageSize = it)
            )
        }
        assertNoHttp()
        verify { spyLog.error("Mobile Inbox fetch rejected: page size out of range", null) }
    }

    @Test
    fun `both page size limits are accepted`() {
        assertTrue(fetch(pageSize = 1) is InboxApiResult.Success)
        assertTrue(fetch(pageSize = 100) is InboxApiResult.Success)
    }

    // endregion

    // region 401

    @Test
    fun `a 401 with the current token refreshes then retries once with a new token`() {
        coEvery { auth.currentToken(any()) } returnsMany listOf(token("old"), token("new"))
        onFetch(response(401), ok(page()))

        assertTrue(fetch(cursor = CURSOR) is InboxApiResult.Success)

        verify(exactly = 1) { auth.refreshRejectedToken() }
        coVerify(exactly = 1) { api.fetchInboxPage("old", PROFILE_ID, CURSOR, 100) }
        coVerify(exactly = 1) { api.fetchInboxPage("new", PROFILE_ID, CURSOR, 100) }
    }

    @Test
    fun `a 401 with a token that is no longer current skips the refresh but still retries`() {
        every { auth.isCurrentToken(any()) } returns false
        coEvery { auth.currentToken(any()) } returnsMany listOf(token("old"), token("newer"))
        onFetch(response(401), ok(page()))

        assertTrue(fetch() is InboxApiResult.Success)

        verify(exactly = 0) { auth.refreshRejectedToken() }
        coVerify(exactly = 2) { auth.currentToken(any()) }
        coVerify { api.fetchInboxPage("newer", any(), any(), any()) }
    }

    @Test
    fun `a second 401 is unauthorized after exactly two sends`() {
        onFetch(response(401), response(401), response(401))

        assertEquals(InboxApiResult.Unauthorized, fetch())

        coVerify(exactly = 2) { api.fetchInboxPage(any(), any(), any(), any()) }
        verify(exactly = 1) { auth.refreshRejectedToken() }
    }

    @Test
    fun `a 401 retry resends the same submit body`() {
        val bodies = mutableListOf<String>()
        coEvery { api.submitInboxStateChanges(any(), capture(bodies)) } returnsMany listOf(
            response(401),
            ok(InboxServerFixtures.acceptedBody(1))
        )

        assertEquals(InboxApiResult.Success(1), submit())

        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
    }

    @Test
    fun `a token failure while retrying a 401 is unavailable`() {
        onFetch(response(401))
        coEvery { auth.currentToken(any()) } returnsMany listOf(token("old")) andThenThrows
            AuthTokenException.TimedOut

        assertUnavailable(UnavailableReason.TOKEN, null, result = fetch())
        coVerify(exactly = 1) { api.fetchInboxPage(any(), any(), any(), any()) }
    }

    @Test
    fun `two concurrent calls that both get a 401 each send at most twice and finish`() = runBlocking {
        // Hold every 401 until both calls have sent the old token, so the race always happens
        val oldSends = AtomicInteger()
        val bothSentOld = CompletableDeferred<Unit>()
        val refreshes = AtomicInteger()
        every { auth.refreshRejectedToken() } answers { refreshes.incrementAndGet() }
        coEvery { auth.currentToken(any()) } answers {
            token(if (refreshes.get() == 0) "old" else "new")
        }
        coEvery { api.fetchInboxPage(any(), any(), any(), any()) } coAnswers {
            if (firstArg<String>() == "old") {
                if (oldSends.incrementAndGet() == 2) bothSentOld.complete(Unit)
                bothSentOld.await()
                response(401)
            } else {
                ok(page())
            }
        }

        val results = withTimeout(TIMEOUT_MS) {
            listOf(
                async(Dispatchers.Default) { client.fetchPage(PROFILE_ID) },
                async(Dispatchers.Default) { client.fetchPage(PROFILE_ID) }
            ).awaitAll()
        }

        assertTrue(results.all { it is InboxApiResult.Success })
        coVerify(exactly = 2) { api.fetchInboxPage("old", any(), any(), any()) }
        coVerify(exactly = 2) { api.fetchInboxPage("new", any(), any(), any()) }
        // The race costs at most one redundant refresh per call, never a loop
        verify(atMost = 2) { auth.refreshRejectedToken() }
    }

    // endregion

    // region identity

    @Test
    fun `a profile change while waiting for the token means no send`() {
        coEvery { auth.currentToken(any()) } coAnswers { token(TOKEN).also { generation++ } }

        assertEquals(InboxApiResult.IdentityChanged, fetch())
        assertEquals(InboxApiResult.IdentityChanged, submit())
        assertNoHttp()
    }

    @Test
    fun `a profile change during the send discards the response`() {
        coEvery { api.fetchInboxPage(any(), any(), any(), any()) } coAnswers {
            generation++
            ok(page())
        }
        coEvery { api.submitInboxStateChanges(any(), any()) } coAnswers {
            generation++
            ok(InboxServerFixtures.acceptedBody(1))
        }

        assertEquals(InboxApiResult.IdentityChanged, fetch())
        assertEquals(InboxApiResult.IdentityChanged, submit())
    }

    @Test
    fun `a profile change during the send discards even an error response`() {
        coEvery { api.fetchInboxPage(any(), any(), any(), any()) } coAnswers {
            generation++
            response(500)
        }

        assertEquals(InboxApiResult.IdentityChanged, fetch())
    }

    @Test
    fun `a profile change after a 401 means no retry`() {
        coEvery { api.fetchInboxPage(any(), any(), any(), any()) } coAnswers {
            generation++
            response(401)
        }

        assertEquals(InboxApiResult.IdentityChanged, fetch())
        coVerify(exactly = 1) { api.fetchInboxPage(any(), any(), any(), any()) }
        verify(exactly = 0) { auth.refreshRejectedToken() }
    }

    // endregion

    // region classification

    @Test
    fun `a 200 page is a success with the parsed page`() {
        onFetch(ok(page(next = nextLink(CURSOR))))

        val page = (fetch() as InboxApiResult.Success).value

        assertEquals(1, page.messages.size)
        assertEquals(CURSOR, page.nextCursor)
    }

    @Test
    fun `a 200 with a malformed page is a protocol failure, never an empty page`() {
        onFetch(ok("""{"data":[{"id":"","attributes":{}}]}"""))

        assertTrue(fetch() is InboxApiResult.ProtocolFailure)
    }

    @Test
    fun `a 200 whose cursor equals the request cursor is a protocol failure`() {
        onFetch(ok(page(next = nextLink(CURSOR))))

        assertTrue(fetch(cursor = CURSOR) is InboxApiResult.ProtocolFailure)
    }

    @Test
    fun `a 200 with no body is a protocol failure`() {
        onFetch(response(200, null))

        assertTrue(fetch() is InboxApiResult.ProtocolFailure)
    }

    @Test
    fun `a 403 with a structured error is forbidden with the detail`() {
        onFetch(response(403, STRUCTURED_403))

        assertEquals(
            InboxApiResult.Forbidden("Invalid `Authorization` header value."),
            fetch()
        )
        verify(exactly = 0) { auth.refreshRejectedToken() }
        coVerify(exactly = 1) { api.fetchInboxPage(any(), any(), any(), any()) }
    }

    @Test
    fun `a 403 that is not a structured error is an edge block`() {
        listOf(CLOUDFLARE_403, null, "", """{"errors":[]}""", """{"message":"no"}""", "[]").forEach {
            onFetch(response(403, it))

            assertUnavailable(UnavailableReason.EDGE_BLOCK, 403, result = fetch())
        }
    }

    @Test
    fun `a 404 is rejected, not unavailable`() {
        onFetch(
            response(
                404,
                """{"errors":[{"status":404,"code":"not_found","detail":"Unable to specify a future revision date."}]}"""
            )
        )

        assertEquals(
            InboxApiResult.Rejected(404, "Unable to specify a future revision date."),
            fetch()
        )
        verify { spyLog.error(match { it.contains("revision or route") }, any()) }
    }

    @Test
    fun `a 422 is an ordinary rejection`() {
        onFetch(response(422, null))

        assertEquals(InboxApiResult.Rejected(422, null), fetch())
    }

    @Test
    fun `a 400 and other 4xx are rejected with the server detail`() {
        onFetch(response(400, STRUCTURED_400))
        assertEquals(InboxApiResult.Rejected(400, "Invalid page cursor"), fetch())

        onFetch(response(405, "not json"))
        assertEquals(InboxApiResult.Rejected(405, null), fetch())
    }

    @Test
    fun `a 429 is rate limited with the retry delay`() {
        onFetch(response(429, null, retryAfterMs = 7_000))

        assertUnavailable(UnavailableReason.RATE_LIMITED, 429, 7_000, fetch())
    }

    @Test
    fun `5xx is a server failure with the retry delay`() {
        listOf(500, 502, 503, 522).forEach {
            onFetch(response(it, "<html>", retryAfterMs = 3_000))

            assertUnavailable(UnavailableReason.SERVER, it, 3_000, fetch())
        }
        onFetch(response(503))
        assertUnavailable(UnavailableReason.SERVER, 503, null, fetch())
    }

    @Test
    fun `a redirect is an edge block`() {
        listOf(301, 302, 307).forEach {
            onFetch(response(it))

            assertUnavailable(UnavailableReason.EDGE_BLOCK, it, result = fetch())
        }
    }

    @Test
    fun `not sent is offline`() {
        onFetch(response(null, sent = false))

        assertUnavailable(UnavailableReason.OFFLINE, null, result = fetch())
    }

    @Test
    fun `no code on a sent request is an io failure`() {
        onFetch(response(null, sent = true))

        assertUnavailable(UnavailableReason.IO, null, result = fetch())
    }

    @Test
    fun `an unexpected status is a protocol failure`() {
        onFetch(response(204))

        val result = fetch()
        assertTrue(result is InboxApiResult.ProtocolFailure)
        assertTrue((result as InboxApiResult.ProtocolFailure).reason.contains("204"))
    }

    @Test
    fun `a transport exception is not swallowed`() {
        coEvery { api.fetchInboxPage(any(), any(), any(), any()) } throws CancellationException("c")

        assertThrows(CancellationException::class.java) { fetch() }
    }

    // endregion

    // region submit

    private fun sentBody(): JSONObject {
        val body = mutableListOf<String>()
        coVerify { api.submitInboxStateChanges(TOKEN, capture(body)) }
        return JSONObject(body.last())
    }

    @Test
    fun `the post body holds the profile and every change in order`() {
        coEvery { api.submitInboxStateChanges(any(), any()) } returns ok(
            InboxServerFixtures.acceptedBody(4)
        )
        val changes = InboxStateChange.values().mapIndexed { index, it ->
            change(id = "tm-$index", change = it, at = CHANGED_AT_MS + index)
        }

        assertEquals(InboxApiResult.Success(4), submit(changes))

        val data = sentBody().getJSONObject("data")
        assertEquals("inbox-message-state-change", data.getString("type"))
        assertFalse(data.has("id"))
        val attributes = data.getJSONObject("attributes")
        assertEquals(PROFILE_ID, attributes.getString("profile_id"))
        val sent = attributes.getJSONArray("changes")
        assertEquals(
            listOf("mark_read", "mark_unread", "archive", "unarchive"),
            List(sent.length()) { sent.getJSONObject(it).getString("change") }
        )
        assertEquals(
            List(4) { "tm-$it" },
            List(sent.length()) { sent.getJSONObject(it).getString("transmission_id") }
        )
        assertEquals("2026-10-15T12:05:00.003+00:00", sent.getJSONObject(3).getString("changed_at"))
        assertEquals(3, sent.getJSONObject(0).length())
    }

    @Test
    fun `changed at is formatted in UTC milliseconds and parses back`() {
        submit()

        val changedAt = sentBody().getJSONObject("data").getJSONObject("attributes")
            .getJSONArray("changes").getJSONObject(0).getString("changed_at")
        assertEquals("2026-10-15T12:05:00.000+00:00", changedAt)
        assertEquals(CHANGED_AT_MS, staticClock.parseRfc3339(changedAt))
    }

    @Test
    fun `0 and 101 changes are rejected locally, 1 and 100 are not`() {
        assertEquals(
            InboxApiResult.Rejected(null, "change count out of range"),
            submit(emptyList())
        )
        assertEquals(
            InboxApiResult.Rejected(null, "change count out of range"),
            submit(List(101) { change(id = "tm-$it") })
        )
        assertNoHttp()

        coEvery { api.submitInboxStateChanges(any(), any()) } returns ok(
            InboxServerFixtures.acceptedBody(100)
        )
        assertEquals(InboxApiResult.Success(100), submit(List(100) { change(id = "tm-$it") }))
    }

    @Test
    fun `a blank transmission id is rejected locally`() {
        listOf("", "  ").forEach {
            assertEquals(
                InboxApiResult.Rejected(null, "blank transmission id"),
                submit(listOf(change(), change(id = it)))
            )
        }
        assertNoHttp()
    }

    @Test
    fun `local validation runs before the not ready checks`() {
        assertTrue(submit(emptyList(), profileId = null) is InboxApiResult.Rejected)
        assertTrue(fetch(profileId = null, pageSize = 0) is InboxApiResult.Rejected)
        assertNoHttp()
    }

    @Test
    fun `an accepted count that differs from the number sent is a protocol failure`() {
        val changes = listOf(change(id = "a"), change(id = "b"))
        coEvery { api.submitInboxStateChanges(any(), any()) } returns ok(
            InboxServerFixtures.acceptedBody(1)
        )

        assertEquals(
            InboxApiResult.ProtocolFailure("accepted_count mismatch", 2, 1),
            submit(changes)
        )
    }

    @Test
    fun `an accepted count that is missing or fractional is a protocol failure`() {
        listOf("""{"data":{"attributes":{}}}""", InboxServerFixtures.acceptedBody(1.5)).forEach {
            coEvery { api.submitInboxStateChanges(any(), any()) } returns ok(it)

            assertTrue(submit() is InboxApiResult.ProtocolFailure)
        }
    }

    @Test
    fun `submit classifies errors like fetch`() {
        coEvery { api.submitInboxStateChanges(any(), any()) } returns response(429, null, 1_000)
        assertUnavailable(UnavailableReason.RATE_LIMITED, 429, 1_000, submit())

        coEvery { api.submitInboxStateChanges(any(), any()) } returns response(503)
        assertUnavailable(UnavailableReason.SERVER, 503, null, submit())

        coEvery { api.submitInboxStateChanges(any(), any()) } returns response(400, STRUCTURED_400)
        assertEquals(InboxApiResult.Rejected(400, "Invalid page cursor"), submit())

        coEvery { api.submitInboxStateChanges(any(), any()) } returns response(403, STRUCTURED_403)
        assertTrue(submit() is InboxApiResult.Forbidden)
    }

    // endregion

    // region logging

    private fun capturedLogs(): List<String> {
        val messages = mutableListOf<String>()
        val throwables = mutableListOf<Throwable?>()
        listOf(
            { verify(atLeast = 0) { spyLog.verbose(capture(messages), captureNullable(throwables)) } },
            { verify(atLeast = 0) { spyLog.debug(capture(messages), captureNullable(throwables)) } },
            { verify(atLeast = 0) { spyLog.info(capture(messages), captureNullable(throwables)) } },
            { verify(atLeast = 0) { spyLog.warning(capture(messages), captureNullable(throwables)) } },
            { verify(atLeast = 0) { spyLog.error(capture(messages), captureNullable(throwables)) } },
            { verify(atLeast = 0) { spyLog.wtf(capture(messages), captureNullable(throwables)) } }
        ).forEach { it() }
        return messages + throwables.filterNotNull().map { it.toString() }
    }

    /** Every path in one run, with the secrets embedded where an error could echo them */
    private fun exercise() {
        val secretBody = """{"errors":[{"detail":"bad"}],"echo":"$TOKEN $PROFILE_ID ${InboxServerFixtures.CONTENT_TITLE}"}"""
        val responses = listOf(
            ok(page()),
            ok(page(next = nextLink(CURSOR))),
            ok(
                """{"data":[{"id":"${InboxServerFixtures.CONTENT_TITLE}","attributes":{"sent_at":"$PROFILE_ID"}}]}"""
            ),
            response(401),
            response(403, STRUCTURED_403),
            response(403, secretBody),
            response(404, secretBody),
            response(400, secretBody),
            response(429, secretBody, 1_000),
            response(503, secretBody, 1_000),
            response(302, secretBody),
            response(null, sent = false),
            response(null)
        )
        responses.forEach {
            onFetch(it, it)
            fetch(cursor = CURSOR)
            coEvery { api.submitInboxStateChanges(any(), any()) } returnsMany listOf(it, it)
            submit()
        }
        coEvery { auth.currentToken(any()) } throws IllegalStateException(
            "provider echoed $TOKEN for $PROFILE_ID"
        )
        fetch()
        submit()
        coEvery { auth.currentToken(any()) } throws AuthTokenException.NotIdentified
        fetch()
        fetch(profileId = null)
        fetch(pageSize = 0)
        submit(emptyList())
        generation = 1
        coEvery { auth.currentToken(any()) } coAnswers { token(TOKEN).also { generation++ } }
        fetch()
    }

    @Test
    fun `no log call on any path carries the token, profile id, content or url`() {
        exercise()

        val logs = capturedLogs()
        assertTrue("expected log output", logs.isNotEmpty())
        val forbidden = listOf(
            TOKEN,
            PROFILE_ID,
            InboxServerFixtures.CONTENT_TITLE,
            "Today only",
            CURSOR,
            TRANSMISSION_ID,
            "http",
            "profile_id",
            "page["
        )
        logs.forEach { line ->
            forbidden.forEach {
                assertFalse("log line leaked '$it': $line", line.contains(it, ignoreCase = true))
            }
        }
    }

    @Test
    fun `each call logs one verbose line with the operation and status`() {
        onFetch(response(503))

        fetch()

        verify(exactly = 1) { spyLog.verbose("Mobile Inbox fetch: 503", null) }
    }

    @Test
    fun `log levels follow the outcome`() {
        assertTrue(fetch() is InboxApiResult.Success)
        verify(exactly = 0) { spyLog.warning(any(), any()) }
        verify(exactly = 0) { spyLog.error(any(), any()) }

        onFetch(response(503))
        fetch()
        verify(exactly = 1) { spyLog.warning(any(), any()) }

        onFetch(response(400, STRUCTURED_400))
        fetch()
        onFetch(ok("{}"))
        fetch()
        verify(exactly = 2) { spyLog.error(any(), any()) }

        fetch(profileId = null)
        verify(exactly = 1) { spyLog.debug(any(), any()) }

        onFetch(response(401), response(401))
        assertEquals(InboxApiResult.Unauthorized, fetch())
        verify(exactly = 2) { spyLog.warning(any(), any()) }

        onFetch(response(403, STRUCTURED_403))
        assertTrue(fetch() is InboxApiResult.Forbidden)
        verify(exactly = 3) { spyLog.error(any(), any()) }
    }

    // endregion

    @Test
    fun `result and change text leave out the cursor and transmission id`() {
        onFetch(ok(page(next = nextLink(CURSOR))))

        val text = fetch().toString() + change().toString()

        assertFalse(text, text.contains(CURSOR))
        assertFalse(text, text.contains(TRANSMISSION_ID))
        assertFalse(text, text.contains(InboxServerFixtures.CONTENT_TITLE))
    }

    @Test
    fun `rejected and forbidden text leave out the server detail`() {
        val echo = """{"errors":[{"detail":"Invalid page cursor $CURSOR"}]}"""

        onFetch(response(400, echo))
        val rejected = fetch()
        onFetch(response(403, echo))
        val forbidden = fetch()

        assertEquals(InboxApiResult.Rejected(400, "Invalid page cursor $CURSOR"), rejected)
        assertTrue(forbidden is InboxApiResult.Forbidden)
        listOf(rejected, forbidden).forEach {
            assertFalse(it.toString(), it.toString().contains(CURSOR))
        }
    }

    @Test
    fun `a failed fetch leaves the token out of the result text`() {
        onFetch(response(403, STRUCTURED_403))

        assertFalse(fetch().toString().contains(TOKEN))
        assertNull((fetch() as? InboxApiResult.Success)?.value)
    }

    private companion object {
        const val TOKEN = "eyJhbGciOiJSUzI1NiJ9.SECRET-PAYLOAD.SECRET-SIGNATURE"
        const val TIMEOUT_MS = 5_000L

        /** `2026-10-15T12:05:00.000+00:00` */
        const val CHANGED_AT_MS = 1_792_065_900_000L
    }
}
