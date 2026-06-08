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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(NativeBridgeMessage.JsReady, JwtObserver().startOn)
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
        val jwtReady = CompletableDeferred<Unit>()
        coEvery { mockAuthTokenManager.currentToken(any()) } returns validatedToken("token")

        JwtObserver(jwtReady).startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(jwtReady.isCompleted)
        assertFalse(jwtReady.isCancelled)
    }

    @Test
    fun `startObserver completes jwtReady even when auth is not enabled`() {
        val jwtReady = CompletableDeferred<Unit>()
        coEvery { mockAuthTokenManager.currentToken(any()) } throws
            AuthTokenException.NoProviderRegistered

        JwtObserver(jwtReady).startObserver()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(jwtReady.isCompleted)
        assertFalse(jwtReady.isCancelled)
    }

    @Test
    fun `stopObserver cancels jwtReady so ProfileMutationObserver awaiter is unblocked`() {
        val jwtReady = CompletableDeferred<Unit>()

        JwtObserver(jwtReady).stopObserver()

        assertTrue(jwtReady.isCancelled)
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
