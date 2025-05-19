package com.klaviyo.forms.bridge

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ObserversTest {
    object MockObserverCollection : ObserverCollection {
        val mockObserver1 = mockk<Observer>(relaxed = true)
        val mockObserver2 = mockk<Observer>(relaxed = true)

        override val observers: List<Observer> = listOf(
            mockObserver1,
            mockObserver2
        )
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
