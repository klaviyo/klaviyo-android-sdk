package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProfileObserverTest {

    private val stubProfile = Profile()
    private val observerSlot = slot<StateChangeObserver>()
    private val jwtObserver = mockk<JwtObserver>(relaxed = true)
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { onStateChange(capture(observerSlot)) } returns Unit
        every { getAsProfile() } returns stubProfile
    }

    @Before
    fun setup() {
        Registry.register<State>(stateMock)
    }

    @After
    fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
    }

    private fun withBridge(): JsBridge {
        val mockBridge = mockk<JsBridge>(relaxed = true)
        Registry.register<JsBridge>(mockBridge)
        ProfileMutationObserver(jwtObserver).startObserver()
        return mockBridge
    }

    @Test
    fun `observer starts on JsReady without waiting for handshake or JWT`() {
        assertEquals(NativeBridgeMessage.JsReady, ProfileMutationObserver(jwtObserver).startOn)
    }

    @Test
    fun `startObserver attaches lambda and sets profile immediately`() {
        val mockBridge = withBridge()
        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
        assert(observerSlot.isCaptured)
        verifyOrder {
            stateMock.onStateChange(any())
            stateMock.getAsProfile()
            mockBridge.profileMutation(stubProfile)
        }
    }

    @Test
    fun `observer calls set profile when profile resets`() {
        val mockBridge = withBridge()
        clearMocks(mockBridge, answers = false)
        observerSlot.captured.invoke(StateChange.ProfileReset(mockk()))
        verifyOrder {
            mockBridge.profileMutation(stubProfile)
            jwtObserver.clearToken()
        }
    }

    @Test
    fun `observer installs replacement profile before clearing outgoing JWT`() {
        val replacement = Profile(email = "new@example.com")
        every { stateMock.getAsProfile() } returns replacement
        val mockBridge = withBridge()
        clearMocks(mockBridge, answers = false)

        observerSlot.captured.invoke(StateChange.ProfileReset(stubProfile))

        verifyOrder {
            mockBridge.profileMutation(replacement)
            jwtObserver.clearToken()
        }
    }

    @Test
    fun `observer ignores other keys`() {
        val mockBridge = withBridge()
        val mockKeyword = mockk<Keyword>(relaxed = true).apply {
            every { name } returns "something_else"
        }
        observerSlot.captured.invoke(StateChange.KeyValue(mockKeyword, "some value"))
        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
    }

    @Test
    fun `observer calls set profile for each identifier key`() {
        val mockBridge = withBridge()
        val keys = listOf(
            "external_id",
            "email",
            "phone_number",
            "anonymous_id"
        )

        for (key in keys) {
            val mockKeyword = mockk<ProfileKey>(relaxed = true).apply {
                every { name } returns key
            }
            observerSlot.captured.invoke(StateChange.ProfileIdentifier(mockKeyword, "value"))
        }

        verify(exactly = keys.count() + 1) { mockBridge.profileMutation(stubProfile) }
    }

    @Test
    fun `observer clears outgoing JWT when anonymous profile becomes identified`() {
        val anonymousProfile = Profile()
        val identifiedProfile = Profile(email = "new@example.com")
        every { stateMock.getAsProfile() } returns anonymousProfile
        val mockBridge = withBridge()
        clearMocks(mockBridge, jwtObserver, answers = false)
        every { stateMock.getAsProfile() } returns identifiedProfile
        val emailKey = mockk<ProfileKey>(relaxed = true).apply {
            every { name } returns "email"
        }

        observerSlot.captured.invoke(StateChange.ProfileIdentifier(emailKey, null))

        verifyOrder {
            mockBridge.profileMutation(identifiedProfile)
            jwtObserver.clearToken()
        }
    }

    @Test
    fun `stopObserver removes the lambda from state change listeners`() {
        withBridge()
        val observer = ProfileMutationObserver(jwtObserver)
        observer.startObserver()
        observer.stopObserver()
        verify(exactly = 1) { stateMock.offStateChange(observerSlot.captured) }
    }

    @Test
    fun `repeated start does not duplicate state subscription or initial delivery`() {
        val mockBridge = mockk<JsBridge>(relaxed = true)
        Registry.register<JsBridge>(mockBridge)
        val observer = ProfileMutationObserver(jwtObserver)

        observer.startObserver()
        observer.startObserver()

        verify(exactly = 1) { stateMock.onStateChange(observer) }
        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
    }

    @Test
    fun `observer can subscribe again after teardown`() {
        val mockBridge = mockk<JsBridge>(relaxed = true)
        Registry.register<JsBridge>(mockBridge)
        val observer = ProfileMutationObserver(jwtObserver)

        observer.startObserver()
        observer.stopObserver()
        observer.startObserver()

        verify(exactly = 2) { stateMock.onStateChange(observer) }
        verify(exactly = 1) { stateMock.offStateChange(observer) }
        verify(exactly = 2) { mockBridge.profileMutation(stubProfile) }
    }
}
