package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenException
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JwtObserverTest : BaseTest() {

    private val mockAuthTokenManager = mockk<AuthTokenManager>()
    private val mockJsBridge = mockk<JsBridge>(relaxed = true)

    private fun validatedToken(rawToken: String): ValidatedToken =
        ValidatedToken(rawToken = rawToken, expiresAtEpochSeconds = 0L, issuedAtEpochSeconds = 0L)

    @Before
    override fun setup() {
        super.setup()
        Registry.register<AuthTokenManager>(mockAuthTokenManager)
        Registry.register<JsBridge>(mockJsBridge)
    }

    @After
    override fun cleanup() {
        Registry.unregister<AuthTokenManager>()
        Registry.unregister<JsBridge>()
        super.cleanup()
    }

    @Test
    fun `startOn defaults to JsReady so JWT is delivered before profile`() {
        assert(JwtObserver().startOn == NativeBridgeMessage.JsReady)
    }

    @Test
    fun `startObserver injects token when fetch succeeds`() {
        val token = "header.payload.signature"
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(token)

        JwtObserver().startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockJsBridge.jwtMutation(token) }
    }

    @Test
    fun `startObserver injects empty string and logs debug when no provider registered`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } throws
            AuthTokenException.NoProviderRegistered

        JwtObserver().startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockJsBridge.jwtMutation("") }
        verify { spyLog.debug(match { it.contains("Auth not enabled") }) }
    }

    @Test
    fun `startObserver injects empty string and logs warning when fetch fails`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } throws
            AuthTokenException.ValidationFailed("Malformed")

        JwtObserver().startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        verify { mockJsBridge.jwtMutation("") }
        verify { spyLog.warning(match { it.contains("Auth token fetch failed") }) }
    }

    @Test
    fun `stopObserver cancels in-flight fetch before injection`() {
        val tokenCompletion = CompletableDeferred<ValidatedToken>()
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers { tokenCompletion.await() }

        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { mockAuthTokenManager.currentToken(any()) }

        observer.stopObserver()
        tokenCompletion.complete(validatedToken("would-be-token"))
        dispatcher.scheduler.advanceUntilIdle()

        verify(inverse = true) { mockJsBridge.jwtMutation(any()) }
    }

    @Test
    fun `startObserver completes jwtReady after successful injection`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("token")

        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(observer.jwtReady.isCompleted)
        assertFalse(observer.jwtReady.isCancelled)
    }

    @Test
    fun `startObserver completes jwtReady even when auth is not enabled`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } throws
            AuthTokenException.NoProviderRegistered

        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(observer.jwtReady.isCompleted)
        assertFalse(observer.jwtReady.isCancelled)
    }

    @Test
    fun `startObserver allocates a fresh jwtReady on reinit after the previous completed`() {
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("session-1")
        val observer = JwtObserver()

        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        val firstSessionDeferred = observer.jwtReady
        assertTrue(firstSessionDeferred.isCompleted)

        observer.stopObserver()

        // Second session — previous deferred is settled, so a fresh one must be allocated
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("session-2")
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        val secondSessionDeferred = observer.jwtReady
        assertNotSame(
            "second startObserver must replace jwtReady once the previous one has settled",
            firstSessionDeferred,
            secondSessionDeferred
        )
        assertTrue(secondSessionDeferred.isCompleted)
        assertFalse(secondSessionDeferred.isCancelled)
        verify(exactly = 1) { mockJsBridge.jwtMutation("session-1") }
        verify(exactly = 1) { mockJsBridge.jwtMutation("session-2") }
    }

    @Test
    fun `startObserver reuses jwtReady when previous session is still in flight`() {
        // Regression guard for MAGE-724 CodeRabbit feedback: if a second startObserver runs before
        // the first fetch settles, the in-flight deferred must be reused so any
        // ProfileMutationObserver waiter that captured it is not orphaned.
        val firstCompletion = CompletableDeferred<ValidatedToken>()
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers { firstCompletion.await() }

        val observer = JwtObserver()
        val initialDeferred = observer.jwtReady

        observer.startObserver()
        dispatcher.scheduler.runCurrent()
        assertSame(
            "first start must not replace a still-pending jwtReady",
            initialDeferred,
            observer.jwtReady
        )

        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("second")
        observer.startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(
            "second start must reuse the pending deferred so waiters are not orphaned",
            initialDeferred,
            observer.jwtReady
        )
        assertTrue(observer.jwtReady.isCompleted)
        verify(exactly = 1) { mockJsBridge.jwtMutation("second") }
    }

    @Test
    fun `double startObserver cancels previous in-flight fetch`() {
        val firstCompletion = CompletableDeferred<ValidatedToken>()
        val secondToken = "second.token.value"
        coEvery { mockAuthTokenManager.currentToken(any()) } coAnswers { firstCompletion.await() }

        val observer = JwtObserver()
        observer.startObserver()
        dispatcher.scheduler.runCurrent()

        // Second start before first fetch completes — should cancel the first job
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken(secondToken)
        observer.startObserver()
        firstCompletion.complete(validatedToken("first.token.value"))
        dispatcher.scheduler.advanceUntilIdle()

        // Only the second token should be injected
        verify(exactly = 1) { mockJsBridge.jwtMutation(secondToken) }
        verify(inverse = true) { mockJsBridge.jwtMutation("first.token.value") }
    }
}
