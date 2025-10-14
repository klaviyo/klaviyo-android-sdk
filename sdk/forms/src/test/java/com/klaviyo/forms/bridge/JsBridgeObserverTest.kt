package com.klaviyo.forms.bridge

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class JsBridgeObserverTest {
    internal object MockObserverCollection : JsBridgeObserverCollection {
        val mockObserver1 = mockk<JsBridgeObserver>(relaxed = true).apply {
            every { startOn } returns NativeBridgeMessage.JsReady
        }
        val mockObserver2 = mockk<JsBridgeObserver>(relaxed = true) {
            every { startOn } returns NativeBridgeMessage.JsReady
        }
        val mockObserver3 = mockk<JsBridgeObserver>(relaxed = true) {
            every { startOn } returns NativeBridgeMessage.HandShook
        }

        override val observers: List<JsBridgeObserver> = listOf(
            mockObserver1,
            mockObserver2,
            mockObserver3
        )
    }

    @Test
    fun `startObservers calls startObserver on all observers`() {
        MockObserverCollection.startObservers(NativeBridgeMessage.JsReady)
        verify(exactly = 1) { MockObserverCollection.mockObserver1.startObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver2.startObserver() }
        verify(exactly = 0) { MockObserverCollection.mockObserver3.startObserver() }
        MockObserverCollection.startObservers(NativeBridgeMessage.HandShook)
        verify(exactly = 1) { MockObserverCollection.mockObserver1.startObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver2.startObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver3.startObserver() }
    }

    @Test
    fun `stopObservers calls stopObserver on all observers`() {
        MockObserverCollection.stopObservers()
        verify(exactly = 1) { MockObserverCollection.mockObserver1.stopObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver2.stopObserver() }
        verify(exactly = 1) { MockObserverCollection.mockObserver3.stopObserver() }
    }

    @Test
    fun `collection contains the expected observers`() {
        val klaviyoObserverCollection = KlaviyoObserverCollection()
        val observers = klaviyoObserverCollection.observers

        assertEquals(4, klaviyoObserverCollection.observers.size)
        assert(observers.any { it is CompanyObserver }) { "Expected CompanyObserver in the collection" }
        assert(observers.any { it is ProfileMutationObserver }) { "Expected ProfileObserver in the collection" }
        assert(observers.any { it is LifecycleObserver }) { "Expected LifecycleObserver in the collection" }
        assert(observers.any { it is ProfileEventObserver }) { "Expected FormsProfileEventObserver in the collection" }
    }
}
