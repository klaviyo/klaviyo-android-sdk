package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LifecycleObserverTest : BaseTest() {
    private val observerSlot = slot<ActivityObserver>()
    private val mockWebViewClient = mockk<WebViewClient>(relaxed = true).apply {
        every { destroyWebView() } returns this
    }

    @Before
    override fun setup() {
        super.setup()
        every { mockLifecycleMonitor.onActivityEvent(capture(observerSlot)) } returns Unit
        Registry.register<WebViewClient>(mockWebViewClient)
        Registry.register<InAppFormsConfig>(InAppFormsConfig(10))
    }

    @After
    override fun cleanup() {
        super.cleanup()
        Registry.unregister<WebViewClient>()
        Registry.unregister<InAppFormsConfig>()
    }

    private fun withBridge(): JsBridge {
        val mockBridge = mockk<JsBridge>(relaxed = true)
        Registry.register<JsBridge>(mockBridge)
        LifecycleObserver().startObserver()
        return mockBridge
    }

    @Test
    fun `handshake is correct`() = assertEquals(
        HandshakeSpec(
            type = "lifecycleEvent",
            version = 1
        ),
        LifecycleObserver().handshake
    )

    @Test
    fun `startObserver attaches and detaches from lifecycle monitor`() {
        val observer = LifecycleObserver()
        observer.startObserver()
        observer.stopObserver()
        verify(exactly = 1) { mockLifecycleMonitor.offActivityEvent(observerSlot.captured) }
    }

    @Test
    fun `other lifecycle events are ignored`() {
        val mockBridge = withBridge()

        observerSlot.captured(ActivityEvent.Resumed(mockActivity))

        verify(inverse = true) {
            mockBridge.dispatchLifecycleEvent(any())
        }
        verify(inverse = true) {
            mockWebViewClient.destroyWebView()
        }
    }

    @Test
    fun `app foregrounded injects foreground lifecycle event`() {
        val mockBridge = withBridge()

        observerSlot.captured(ActivityEvent.FirstStarted(mockActivity))

        verify {
            mockBridge.dispatchLifecycleEvent(
                JsBridge.LifecycleEventType.foreground
            )
        }
        verify(inverse = true) { mockWebViewClient.destroyWebView() }
        verify(inverse = true) { mockWebViewClient.initializeWebView() }
    }

    @Test
    fun `app backgrounded injects background lifecycle event`() {
        val mockBridge = withBridge()

        observerSlot.captured(ActivityEvent.AllStopped())

        verify {
            mockBridge.dispatchLifecycleEvent(
                JsBridge.LifecycleEventType.background
            )
        }
    }

    @Test
    fun `app foregrounded within session timeout injects foreground lifecycle event`() {
        val mockBridge = withBridge()

        observerSlot.captured(ActivityEvent.AllStopped())
        staticClock.execute(1_000)
        observerSlot.captured(ActivityEvent.FirstStarted(mockActivity))

        verify {
            mockBridge.dispatchLifecycleEvent(
                JsBridge.LifecycleEventType.foreground
            )
        }
        verify(inverse = true) { mockWebViewClient.destroyWebView() }
    }

    @Test
    fun `app foregrounded after session timeout resets webview`() {
        val mockBridge = withBridge()

        observerSlot.captured(ActivityEvent.AllStopped())
        staticClock.execute(10_000)
        observerSlot.captured(ActivityEvent.FirstStarted(mockActivity))

        verify(inverse = true) {
            mockBridge.dispatchLifecycleEvent(
                JsBridge.LifecycleEventType.foreground
            )
        }
        verify() { mockWebViewClient.destroyWebView() }
        verify { mockWebViewClient.initializeWebView() }
    }
}
