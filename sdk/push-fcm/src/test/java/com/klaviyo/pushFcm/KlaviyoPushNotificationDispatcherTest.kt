package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KlaviyoPushNotificationDispatcherTest : BaseTest() {

    private val pushDispatcher = KlaviyoPushNotificationDispatcher()

    private fun observe(observer: KlaviyoNotificationObserver) = observer.also {
        pushDispatcher.onKlaviyoNotification(it)
    }

    private fun message(messageId: String? = UUID.randomUUID().toString()) = mockk<RemoteMessage>().apply {
        every { this@apply.messageId } returns messageId
    }

    @Test
    fun `dispatch reaches every observer`() {
        val received = mutableListOf<String>()
        observe { received += "first" }
        observe { received += "second" }

        pushDispatcher.dispatch(message())

        assertEquals(listOf("first", "second"), received)
    }

    @Test
    fun `dispatch reaches observers once per message ID`() {
        var calls = 0
        observe { calls++ }
        val msg = message()

        pushDispatcher.dispatch(msg)
        pushDispatcher.dispatch(msg)

        assertEquals(1, calls)
    }

    @Test
    fun `dispatch reaches observers every time when the message has no ID`() {
        var calls = 0
        observe { calls++ }
        val msg = message(messageId = null)

        pushDispatcher.dispatch(msg)
        pushDispatcher.dispatch(msg)

        assertEquals(2, calls)
    }

    @Test
    fun `dispatch treats a blank message ID as missing`() {
        var calls = 0
        observe { calls++ }

        pushDispatcher.dispatch(message(messageId = ""))
        pushDispatcher.dispatch(message(messageId = ""))

        assertEquals(2, calls)
    }

    @Test
    fun `a throwing observer is logged and does not stop later observers`() {
        var reached = false
        observe { throw IllegalStateException("boom") }
        observe { reached = true }

        pushDispatcher.dispatch(message())

        assertTrue(reached)
        verify { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `registering the same observer twice dispatches to it once`() {
        var calls = 0
        val observer = observe { calls++ }
        pushDispatcher.onKlaviyoNotification(observer)

        pushDispatcher.dispatch(message())

        assertEquals(1, calls)
    }

    @Test
    fun `a removed observer is not dispatched to`() {
        var calls = 0
        val observer = observe { calls++ }
        pushDispatcher.offKlaviyoNotification(observer)

        pushDispatcher.dispatch(message())

        assertEquals(0, calls)
    }

    @Test
    fun `dispatch does not read the message when nothing is observing`() {
        val msg = mockk<RemoteMessage>()

        pushDispatcher.dispatch(msg)

        verify(exactly = 0) { msg.messageId }
    }
}
