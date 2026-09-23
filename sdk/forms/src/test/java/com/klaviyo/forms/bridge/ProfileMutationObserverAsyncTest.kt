package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProfileMutationObserverAsyncTest : BaseTest() {

    private val stubProfile = Profile(email = EMAIL)
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { getAsProfile() } returns stubProfile
    }
    private val mockBridge = mockk<JsBridge>(relaxed = true)
    private val mockAuth = mockk<AuthTokenManager>().apply {
        every { onTokenRefresh(any()) } just runs
        every { offTokenRefresh(any()) } just runs
    }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(stateMock)
        Registry.register<JsBridge>(mockBridge)
        Registry.register<AuthTokenManager>(mockAuth)
    }

    @After
    override fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
        Registry.unregister<AuthTokenManager>()
        super.cleanup()
    }

    @Test
    fun `profile delivery does not wait for a slow initial JWT`() {
        val token = CompletableDeferred<ValidatedToken>()
        coEvery { mockAuth.currentToken(any()) } coAnswers { token.await() }
        val jwtObserver = JwtObserver()

        jwtObserver.startObserver()
        dispatcher.scheduler.runCurrent()
        ProfileMutationObserver().startObserver()

        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
        verify(exactly = 0) { mockBridge.jwtMutation(any()) }

        token.complete(ValidatedToken("late", 0L, 0L))
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.jwtMutation("late") }
        jwtObserver.stopObserver()
    }

    @Test
    fun `initial JWT may arrive before profile delivery`() {
        coEvery { mockAuth.currentToken(any()) } returns ValidatedToken("early", 0L, 0L)
        val jwtObserver = JwtObserver()

        jwtObserver.startObserver()
        dispatcher.scheduler.advanceUntilIdle()
        ProfileMutationObserver().startObserver()

        verifyOrder {
            mockBridge.jwtMutation("early")
            mockBridge.profileMutation(stubProfile)
        }
        jwtObserver.stopObserver()
    }
}
