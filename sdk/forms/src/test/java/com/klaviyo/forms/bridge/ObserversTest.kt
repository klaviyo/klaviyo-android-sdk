package com.klaviyo.forms.bridge

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ObserversTest {
    internal object MockObserverCollection : ObserverCollection {
        val mockObserver1 = mockk<Observer>(relaxed = true) {
            every { handshake } returns HandshakeSpec("mockObserver1", 1)
        }
        val mockObserver2 = mockk<Observer>(relaxed = true) {
            every { handshake } returns HandshakeSpec("mockObserver2", 1)
        }

        override val observers: List<Observer> = listOf(
            mockObserver1,
            mockObserver2
        )
    }

    @Test
    fun `test compileJson returns correct JSON for observers`() {
        val expectedJson = """[{"type":"mockObserver1","version":1},{"type":"mockObserver2","version":1}]"""
        val actualJson = MockObserverCollection.handshake.compileJson()
        assert(actualJson == expectedJson) { "Expected $expectedJson but got $actualJson" }
    }

    @Test
    fun `test startObservers calls startObserver on all observers`() {
        MockObserverCollection.startObservers()
        verify(exactly = 1) { MockObserverCollection.mockObserver1.startObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver2.startObserver() }
    }

    @Test
    fun `test stopObservers calls stopObserver on all observers`() {
        MockObserverCollection.stopObservers()
        verify(exactly = 1) { MockObserverCollection.mockObserver1.stopObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver2.stopObserver() }
    }
}
