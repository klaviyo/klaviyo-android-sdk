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
    fun `observer starts on JsReady so profile is available before handshake`() {
        assertEquals(NativeBridgeMessage.JsReady, ProfileMutationObserver(jwtObserver).startOn)
    }

    @Test
    fun `startObserver attaches lambda and sets profile immediately`() {
        val mockBridge = withBridge()
        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
        assert(observerSlot.isCaptured)
    }

    @Test
    fun `observer calls set profile when profile resets`() {
        val mockBridge = withBridge()
        observerSlot.captured.invoke(StateChange.ProfileReset(mockk()))
        verify(exactly = 2) { mockBridge.profileMutation(stubProfile) }
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
    fun `stopObserver removes the lambda from state change listeners`() {
        withBridge()
        val observer = ProfileMutationObserver(jwtObserver)
        observer.startObserver()
        observer.stopObserver()
        verify(exactly = 1) { stateMock.offStateChange(observerSlot.captured) }
    }

    @Test
    fun `repeated start re-sends the profile without subscribing again`() {
        val bridge = mockk<JsBridge>(relaxed = true)
        Registry.register<JsBridge>(bridge)
        val observer = ProfileMutationObserver(jwtObserver)

        observer.startObserver()
        observer.startObserver()

        verify(exactly = 1) { stateMock.onStateChange(any()) }
        verify(exactly = 2) { bridge.profileMutation(stubProfile) }
    }

    @Test
    fun `queued state callback from prior session cannot mutate restarted bridge`() {
        val bridge = mockk<JsBridge>(relaxed = true)
        Registry.register<JsBridge>(bridge)
        val observer = ProfileMutationObserver(jwtObserver)
        observer.startObserver()
        val priorCallback = observerSlot.captured
        observer.stopObserver()
        observer.startObserver()
        clearMocks(bridge, answers = false)

        priorCallback.invoke(StateChange.ProfileReset(stubProfile))

        verify(exactly = 0) { bridge.profileMutation(any()) }
    }
}
