package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProfileObserverTest : BaseTest() {

    private val stubProfile = Profile()
    private val observerSlot = slot<(Keyword?, Any?) -> Unit>()
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { onStateChange(capture(observerSlot)) } returns Unit
        every { getAsProfile() } returns stubProfile
    }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(stateMock)
    }

    @After
    override fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<OnsiteBridge>()
        super.cleanup()
    }

    private fun withBridge(): OnsiteBridge {
        val mockBridge = mockk<OnsiteBridge>(relaxed = true)
        Registry.register<OnsiteBridge>(mockBridge)
        ProfileObserver().startObserver()
        return mockBridge
    }

    @Test
    fun `handshake is correct`() = assertEquals(
        HandshakeSpec(
            type = "profileMutation",
            version = 1
        ),
        ProfileObserver().handshake
    )

    @Test
    fun `startObserver attaches lambda and sets profile immediately`() {
        val mockBridge = withBridge()
        verify(exactly = 1) { mockBridge.setProfile(stubProfile) }
        assert(observerSlot.isCaptured)
    }

    @Test
    fun `observer calls set profile when key is null`() {
        val mockBridge = withBridge()
        observerSlot.captured.invoke(null, null)
        verify(exactly = 2) { mockBridge.setProfile(stubProfile) }
    }

    @Test
    fun `observer ignores other keys`() {
        val mockBridge = withBridge()
        val mockKeyword = mockk<Keyword>(relaxed = true).apply {
            every { name } returns "something_else"
        }
        observerSlot.captured.invoke(mockKeyword, "some value")
        verify(exactly = 1) { mockBridge.setProfile(stubProfile) }
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
            val mockKeyword = mockk<Keyword>(relaxed = true).apply {
                every { name } returns key
            }
            observerSlot.captured.invoke(mockKeyword, "value")
        }

        verify(exactly = keys.count() + 1) { mockBridge.setProfile(stubProfile) }
    }

    @Test
    fun `stopObserver removes the lambda from state change listeners`() {
        withBridge()
        val observer = ProfileObserver()
        observer.startObserver()
        observer.stopObserver()
        verify(exactly = 1) { stateMock.offStateChange(observerSlot.captured) }
    }
}
