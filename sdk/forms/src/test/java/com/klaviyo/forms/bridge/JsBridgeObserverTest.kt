package com.klaviyo.forms.bridge

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class JsBridgeObserverTest {
    internal object MockObserverCollection : JsBridgeObserverCollection {
        val mockObserver1 = mockk<JsBridgeObserver>(relaxed = true) {
            every { handshake } returns HandshakeSpec("mockObserver1", 1)
        }
        val mockObserver2 = mockk<JsBridgeObserver>(relaxed = true) {
            every { handshake } returns HandshakeSpec("mockObserver2", 1)
        }

        override val observers: List<JsBridgeObserver> = listOf(
            mockObserver1,
            mockObserver2
        )
    }

    @Test
    fun `compileJson returns correct JSON for observers`() {
        val expectedJson = """[{"type":"mockObserver1","version":1},{"type":"mockObserver2","version":1}]"""
        val actualJson = MockObserverCollection.handshake.compileJson()
        assert(actualJson == expectedJson) { "Expected $expectedJson but got $actualJson" }
    }

    @Test
    fun `startObservers calls startObserver on all observers`() {
        MockObserverCollection.startObservers()
        verify(exactly = 1) { MockObserverCollection.mockObserver1.startObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver2.startObserver() }
    }

    @Test
    fun `stopObservers calls stopObserver on all observers`() {
        MockObserverCollection.stopObservers()
        verify(exactly = 1) { MockObserverCollection.mockObserver1.stopObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver2.stopObserver() }
    }

    @Test
    fun `handshake returns correct list of HandshakeSpec`() {
        val expectedHandshake = listOf(
            HandshakeSpec("mockObserver1", 1),
            HandshakeSpec("mockObserver2", 1)
        )
        val actualHandshake = MockObserverCollection.handshake
        assert(actualHandshake == expectedHandshake) { "Expected $expectedHandshake but got $actualHandshake" }
    }

    @Test
    fun `collection contains the expected observers`() {
        val klaviyoObserverCollection = KlaviyoObserverCollection()
        val observers = klaviyoObserverCollection.observers

        assertEquals(2, klaviyoObserverCollection.observers.size)
        assert(observers.any { it is ProfileObserver }) { "Expected ProfileObserver in the collection" }
        assert(observers.any { it is LifecycleObserver }) { "Expected LifecycleObserver in the collection" }
    }
}
