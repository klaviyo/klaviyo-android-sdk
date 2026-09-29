package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.auth.TokenRefreshObserver
import com.klaviyo.core.auth.ValidatedToken
import com.klaviyo.fixtures.BaseTest
import io.mockk.CapturingSlot
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProfileJwtTransitionMatrixTest : BaseTest() {
    private val stateObservers = mutableListOf<StateChangeObserver>()
    private val state = mockk<State>(relaxed = true).apply {
        every { onStateChange(any()) } answers { stateObservers.add(firstArg<StateChangeObserver>()) }
        every { offStateChange(any()) } just runs
    }
    private val bridge = mockk<JsBridge>(relaxed = true)
    private val auth = mockk<AuthTokenManager>().apply {
        every { onTokenRefresh(any()) } just runs
        every { offTokenRefresh(any()) } just runs
        every { onTokenInvalidated(any()) } just runs
        every { offTokenInvalidated(any()) } just runs
        every { isCurrentToken(any()) } returns true
    }

    private fun identifierKey(identifierName: String): ProfileKey =
        mockk<ProfileKey>().also { every { it.name } returns identifierName }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(state)
        Registry.register<JsBridge>(bridge)
        Registry.register<AuthTokenManager>(auth)
    }

    @After
    override fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
        Registry.unregister<AuthTokenManager>()
        super.cleanup()
    }

    @Test
    fun `live webview leaves unchanged profile and JWT untouched`() = runTest(dispatcher) {
        val profile = Profile(email = EMAIL, externalId = EXTERNAL_ID)
        every { state.getAsProfile() } returns profile
        coEvery { auth.currentToken(any()) } returns ValidatedToken("retained", 0L, 0L)
        val collection = KlaviyoObserverCollection()

        collection.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(bridge, answers = false)
        emit(StateChange.ProfileAttributes(profile))

        verify(inverse = true) { bridge.profileMutation(any()) }
        verify(inverse = true) { bridge.jwtMutation(any()) }
        collection.stopObservers()
    }

    @Test
    fun `live webview retains JWT through compatible enrichment and reduction`() = runTest(
        dispatcher
    ) {
        val retained = Profile(email = EMAIL, externalId = EXTERNAL_ID)
        val enriched = Profile(email = EMAIL, externalId = EXTERNAL_ID, phoneNumber = PHONE)
        val reduced = Profile(email = EMAIL)
        every { state.getAsProfile() } returns retained andThen enriched andThen reduced
        coEvery { auth.currentToken(any()) } returns ValidatedToken("retained", 0L, 0L)
        val collection = KlaviyoObserverCollection()

        collection.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        emit(StateChange.ProfileIdentifier(identifierKey("phone_number"), null))
        emit(StateChange.ProfileIdentifier(identifierKey("external_id"), EXTERNAL_ID))

        verify(exactly = 1) { bridge.jwtMutation("retained") }
        verifyOrder {
            bridge.profileMutation(enriched)
            bridge.profileMutation(reduced)
        }
        verify(inverse = true) { bridge.jwtMutation("") }
        collection.stopObservers()
    }

    @Test
    fun `live webview fences conflicting identifier overlap before a fresh JWT`() = runTest(
        dispatcher
    ) {
        assertLiveReplacement(
            outgoing = Profile(email = EMAIL, externalId = EXTERNAL_ID),
            replacement = Profile(email = "conflict@example.com", externalId = EXTERNAL_ID)
        )
    }

    @Test
    fun `live webview fences no overlap replacement before a fresh JWT`() = runTest(dispatcher) {
        assertLiveReplacement(
            outgoing = Profile(email = EMAIL),
            replacement = Profile(externalId = "replacement")
        )
    }

    @Test
    fun `live webview fences anonymous to identified transition before a fresh JWT`() = runTest(
        dispatcher
    ) {
        val anonymous = Profile().apply { anonymousId = "anonymous" }
        val identified = Profile(email = EMAIL)
        every { state.getAsProfile() } returns anonymous andThen identified
        coEvery { auth.currentToken(any()) } returns ValidatedToken("outgoing", 0L, 0L)
        val collection = KlaviyoObserverCollection()

        collection.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(bridge, answers = false)
        emit(StateChange.ProfileIdentifier(identifierKey("email"), null))

        verifyOrder {
            bridge.jwtMutation("")
            bridge.profileMutation(identified)
        }
        verify(inverse = true) { bridge.jwtMutation("outgoing") }
        collection.stopObservers()
    }

    @Test
    fun `live webview fences reset before a fresh JWT`() = runTest(dispatcher) {
        assertLiveReplacement(
            outgoing = Profile(email = EMAIL),
            replacement = Profile()
        )
    }

    @Test
    fun `fresh webview matrix delivers only fresh JWT after replacement`() = runTest(dispatcher) {
        val refresh = refreshObserver()
        val outgoing = Profile(email = "old@example.com")
        val replacement = Profile(email = "new@example.com")
        every { state.getAsProfile() } returns outgoing andThen replacement
        coEvery { auth.currentToken(any()) } returns ValidatedToken("old", 0L, 0L)
        val first = KlaviyoObserverCollection()
        first.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        first.stopObservers()
        clearMocks(bridge, answers = false)
        val outgoingFetch = CompletableDeferred<ValidatedToken>()
        coEvery { auth.currentToken(any()) } coAnswers { outgoingFetch.await() }
        val fresh = KlaviyoObserverCollection()

        fresh.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(bridge, answers = false)
        emit(StateChange.ProfileReset(outgoing))
        refresh.captured("fresh") { true }
        outgoingFetch.complete(ValidatedToken("old", 0L, 0L))
        dispatcher.scheduler.advanceUntilIdle()

        verify(inverse = true) { bridge.jwtMutation("old") }
        verifyOrder {
            bridge.jwtMutation("")
            bridge.profileMutation(replacement)
            bridge.jwtMutation("fresh")
        }
        fresh.stopObservers()
    }

    private fun refreshObserver(): CapturingSlot<TokenRefreshObserver> = slot<TokenRefreshObserver>().also {
        every { auth.onTokenRefresh(capture(it)) } just runs
    }

    private suspend fun assertLiveReplacement(outgoing: Profile, replacement: Profile) {
        every { state.getAsProfile() } returns outgoing andThen replacement
        coEvery { auth.currentToken(any()) } returns ValidatedToken("outgoing", 0L, 0L)
        val collection = KlaviyoObserverCollection()

        collection.startObservers(NativeBridgeMessage.JsReady)
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(bridge, answers = false)
        emit(StateChange.ProfileReset(outgoing))

        verifyOrder {
            bridge.jwtMutation("")
            bridge.profileMutation(replacement)
        }
        verify(inverse = true) { bridge.jwtMutation("outgoing") }
        collection.stopObservers()
    }

    private fun emit(change: StateChange) = stateObservers.forEach { it(change) }

    private companion object {
        const val EMAIL = "email@example.com"
        const val EXTERNAL_ID = "external-id"
        const val PHONE = "+15555555555"
    }
}
