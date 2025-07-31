package com.klaviyo.forms.bridge

import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class JsBridgeObserverTest {
    internal object MockObserverCollection : JsBridgeObserverCollection {
        val mockObserver1 = mockk<JsBridgeObserver>(relaxed = true)
        val mockObserver2 = mockk<JsBridgeObserver>(relaxed = true)

        override val observers: List<JsBridgeObserver> = listOf(
            mockObserver1,
            mockObserver2
        )
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
    fun `collection contains the expected observers`() {
        val klaviyoObserverCollection = KlaviyoObserverCollection()
        val observers = klaviyoObserverCollection.observers

        assertEquals(4, klaviyoObserverCollection.observers.size)
        assert(observers.any { it is CompanyObserver }) { "Expected CompanyObserver in the collection" }
        assert(observers.any { it is ProfileObserver }) { "Expected ProfileObserver in the collection" }
        assert(observers.any { it is LifecycleObserver }) { "Expected LifecycleObserver in the collection" }
        assert(observers.any { it is FormsProfileEventObserver }) { "Expected FormsProfileEventObserver in the collection" }
    }
}
