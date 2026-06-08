package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ProfileMutationObserver] behaviour when a [jwtReady] deferred is provided —
 * i.e. the coordination path used by [KlaviyoObserverCollection] to prevent profile injection
 * from racing ahead of JWT delivery.
 */
class ProfileMutationObserverAsyncTest : BaseTest() {

    private val stubProfile = Profile()
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { getAsProfile() } returns stubProfile
    }
    private val mockBridge = mockk<JsBridge>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(stateMock)
        Registry.register<JsBridge>(mockBridge)
    }

    @After
    override fun cleanup() {
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
        super.cleanup()
    }

    @Test
    fun `startObserver awaits jwtReady before injecting profile`() {
        val jwtReady = CompletableDeferred<Unit>()
        ProfileMutationObserver(jwtReady).startObserver()
        dispatcher.scheduler.runCurrent()

        // JWT not yet delivered — profile must not have been injected
        verify(inverse = true) { mockBridge.profileMutation(any()) }

        // JWT arrives — profile injection should now proceed
        jwtReady.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
    }

    @Test
    fun `startObserver does not inject profile if jwtReady is cancelled`() {
        val jwtReady = CompletableDeferred<Unit>()
        val observer = ProfileMutationObserver(jwtReady)
        observer.startObserver()
        dispatcher.scheduler.runCurrent()

        // WebView destroyed before JWT delivered — deferred is cancelled
        jwtReady.cancel()
        dispatcher.scheduler.advanceUntilIdle()

        verify(inverse = true) { mockBridge.profileMutation(any()) }
    }
}
