package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.state.ProfileEventObserver as AnalyticsEventObserver
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.InAppFormsConfig
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FormsProfileEventObserverTest : BaseTest() {
    private val state = mockk<State>(relaxed = true)
    private val jsBridge = mockk<JsBridge>(relaxed = true)
    private val subscriptions = mutableListOf<AnalyticsEventObserver>()
    private val delivered = mutableListOf<String?>()
    private val history = listOf(event("launch"))
    private var generation = 0L
    private val observer = ProfileEventObserver()

    private fun event(id: String) = Event(id).apply { uniqueId = id }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<State>(state)
        Registry.register<JsBridge>(jsBridge)
        Registry.register<InAppFormsConfig>(InAppFormsConfig())
        every { state.onProfileEvent(any()) } answers { subscriptions.add(firstArg()); Unit }
        every { state.offProfileEvent(any()) } answers { subscriptions.remove(firstArg()); Unit }
        every { state.getBufferedEvents() } returns history
        every { state.getBufferedEvents(any()) } answers { if (firstArg<Long>() > 0) history else emptyList() }
        every { state.bufferedEventGeneration } answers { generation }
        every { jsBridge.profileEvent(any()) } answers { delivered.add(firstArg<Event>().uniqueId); Unit }
    }

    @After
    override fun cleanup() {
        observer.stopObserver()
        Registry.unregister<State>()
        Registry.unregister<JsBridge>()
        Registry.unregister<InAppFormsConfig>()
        super.cleanup()
    }

    @Test
    fun `configured replay window selects history before live delivery`() {
        Registry.register<InAppFormsConfig>(InAppFormsConfig(eventReplayWindow = 30.seconds))
        every { state.getBufferedEvents() } returns emptyList()
        every { state.getBufferedEvents(any()) } answers {
            if (firstArg<Long>() == 30_000L) history else emptyList()
        }
        observer.startObserver()
        subscriptions.single()(event("live"))
        assertEquals(listOf("launch", "live"), delivered)
    }

    @Test
    fun `zero replay window still delivers live events`() {
        Registry.register<InAppFormsConfig>(InAppFormsConfig(eventReplayWindow = 0.seconds))
        observer.startObserver()
        subscriptions.single()(event("live"))
        assertEquals(listOf("live"), delivered)
    }

    @Test
    fun `repeated starts register and replay once`() {
        observer.startObserver()
        observer.startObserver()
        subscriptions.forEach { it(event("live")) }
        assertEquals(listOf("launch", "live"), delivered)
        assertEquals(1, subscriptions.size)
    }

    @Test
    fun `event arriving during snapshot is delivered once across handoff`() {
        every { state.getBufferedEvents() } answers {
            subscriptions.forEach { it(event("during")) }
            history + event("during")
        }
        every { state.getBufferedEvents(any()) } answers {
            subscriptions.forEach { it(event("during")) }
            history + event("during")
        }
        observer.startObserver()
        subscriptions.single()(event("after"))
        assertEquals(listOf("launch", "during", "after"), delivered)
    }

    @Test
    fun `event arriving after snapshot read is not missed`() {
        every { state.getBufferedEvents() } answers {
            subscriptions.forEach { it(event("during")) }
            history
        }
        every { state.getBufferedEvents(any()) } answers {
            subscriptions.forEach { it(event("during")) }
            history
        }
        observer.startObserver()
        assertEquals(listOf("launch", "during"), delivered)
    }

    @Test
    fun `stopped session cannot deliver a captured live callback`() {
        observer.startObserver()
        val oldCallback = subscriptions.single()
        observer.stopObserver()
        oldCallback(event("stale"))
        assertEquals(listOf("launch"), delivered)
    }

    @Test
    fun `identity change fences queued history and live deliveries`() {
        val queued = mutableListOf<() -> Unit>()
        every { mockThreadHelper.runOnUiThread(any()) } answers { queued.add(firstArg()); Unit }
        observer.startObserver()
        subscriptions.single()(event("old-live"))
        generation++
        queued.toList().forEach { it() }
        assertEquals(emptyList<String>(), delivered)
        queued.clear()
        subscriptions.single()(event("new-live"))
        queued.toList().forEach { it() }
        assertEquals(listOf("new-live"), delivered)
    }
}
