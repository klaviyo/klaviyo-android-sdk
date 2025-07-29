package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.core.Registry
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class FormsProfileEventObserverTest {
    private val mockJsBridge = mockk<JsBridge>(relaxed = true)

    private val testEvent = Event(
        metric = "Fate Sealed",
        properties = mapOf(
            EventKey.CUSTOM("name") to "Anna Karenina",
            EventKey.CUSTOM("action") to "Adultery",
            EventKey.CUSTOM("location") to "Saint Petersburg"
        )
    )

    @Before
    fun setup() {
        Registry.register<JsBridge>(mockJsBridge)
    }

    @Test
    fun `invoke event broadcast`() {
        FormsProfileEventObserver().invoke(testEvent)
        verify { mockJsBridge.profileEvent(testEvent) }
    }

    @After
    fun cleanup() {
        Registry.unregister<JsBridge>()
        unmockkAll()
    }
}
