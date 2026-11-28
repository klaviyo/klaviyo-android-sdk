package com.klaviyo.pushFcm

import android.content.Intent
import com.klaviyo.core.PushTapDestination
import com.klaviyo.core.utils.AdvancedAPI
import com.klaviyo.fixtures.BaseTest
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(AdvancedAPI::class)
class KlaviyoPushTapDispatcherTest : BaseTest() {

    private val tapDispatcher = KlaviyoPushTapDispatcher()
    private val intent = mockk<Intent>(relaxed = true)

    private fun observe(observer: PushTapObserver) = observer.also {
        tapDispatcher.onPushTap(it)
    }

    @Test
    fun `dispatch reaches every observer with the tap`() {
        val received = mutableListOf<Pair<String, PushTapDestination>>()
        observe { _, destination -> received += "first" to destination }
        observe { _, destination -> received += "second" to destination }

        tapDispatcher.dispatch(intent, PushTapDestination.OpenApp)

        assertEquals(
            listOf("first" to PushTapDestination.OpenApp, "second" to PushTapDestination.OpenApp),
            received
        )
    }

    @Test
    fun `a throwing observer is logged and does not stop later observers`() {
        var reached = false
        observe { _, _ -> throw IllegalStateException("boom") }
        observe { _, _ -> reached = true }

        tapDispatcher.dispatch(intent, PushTapDestination.External("https://example.com"))

        assertTrue(reached)
        verify { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `registering the same observer twice dispatches to it once`() {
        var calls = 0
        val observer = observe { _, _ -> calls++ }
        tapDispatcher.onPushTap(observer)

        tapDispatcher.dispatch(intent, PushTapDestination.OpenApp)

        assertEquals(1, calls)
    }

    @Test
    fun `a removed observer is not dispatched to and the others still are`() {
        var removedCalls = 0
        var keptCalls = 0
        val removed = observe { _, _ -> removedCalls++ }
        observe { _, _ -> keptCalls++ }
        tapDispatcher.offPushTap(removed)

        tapDispatcher.dispatch(intent, PushTapDestination.OpenApp)

        assertEquals(0, removedCalls)
        assertEquals(1, keptCalls)
    }
}
