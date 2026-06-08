package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ProfileMutationObserver] behaviour when a [JwtObserver] is provided —
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
        // JwtObserver created but not started — jwtReady is the initial incomplete deferred,
        // which we complete manually below to simulate JWT delivery.
        val jwtObserver = JwtObserver()
        ProfileMutationObserver(jwtObserver).startObserver()
        dispatcher.scheduler.runCurrent()

        // JWT not yet delivered — profile must not have been injected
        verify(inverse = true) { mockBridge.profileMutation(any()) }

        // JWT arrives — profile injection should now proceed
        jwtObserver.jwtReady.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
    }

    @Test
    fun `startObserver does not inject profile if jwtReady is cancelled`() {
        val jwtObserver = JwtObserver()
        val observer = ProfileMutationObserver(jwtObserver)
        observer.startObserver()
        dispatcher.scheduler.runCurrent()

        // WebView destroyed before JWT delivered — deferred is cancelled
        jwtObserver.jwtReady.cancel()
        dispatcher.scheduler.advanceUntilIdle()

        verify(inverse = true) { mockBridge.profileMutation(any()) }
    }

    @Test
    fun `startObserver works on reinit after stop — scope must not be permanently cancelled`() {
        // Regression test: the old implementation called scope.cancel() in stopObserver(),
        // which permanently destroyed the scope and made subsequent startObserver calls a no-op.
        val jwtObserver = JwtObserver()
        val observer = ProfileMutationObserver(jwtObserver)

        // First session: start and immediately stop
        observer.startObserver()
        observer.stopObserver()

        // Second session: should work even though stopObserver was previously called
        observer.startObserver()
        jwtObserver.jwtReady.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { mockBridge.profileMutation(stubProfile) }
    }
}
