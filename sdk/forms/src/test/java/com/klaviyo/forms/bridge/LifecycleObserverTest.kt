package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Test

class LifecycleObserverTest : BaseTest() {
    private val observerSlot = slot<ActivityObserver>()
    private val mockWebViewClient = mockk<WebViewClient>(relaxed = true).apply {
        every { destroyWebView() } returns this
    }
    private val mockBridge = mockk<JsBridge>(relaxed = true)
    private lateinit var observer: LifecycleObserver

    @Before
    override fun setup() {
        super.setup()
        every { mockLifecycleMonitor.onActivityEvent(capture(observerSlot)) } returns Unit
        Registry.register<InAppFormsConfig>(InAppFormsConfig(10.seconds))
        Registry.register<PresentationManager>(mockk<PresentationManager>(relaxed = true))
        Registry.register<WebViewClient>(mockWebViewClient)
        Registry.register<JsBridge>(mockBridge)
        Registry.register<NativeBridge>(mockk<NativeBridge>(relaxed = true))

        observer = LifecycleObserver().apply { startObserver() }
    }

    @After
    override fun cleanup() {
        super.cleanup()
        Registry.unregister<InAppFormsConfig>()
        Registry.unregister<PresentationManager>()
        Registry.unregister<WebViewClient>()
        Registry.unregister<JsBridge>()
        Registry.unregister<NativeBridge>()
    }

    @Test
    fun `startObserver attaches and detaches from lifecycle monitor`() {
        observer.stopObserver()
        verify(exactly = 1) { mockLifecycleMonitor.offActivityEvent(observerSlot.captured) }
    }

    @Test
    fun `other lifecycle events are ignored`() {
        observerSlot.captured(ActivityEvent.Resumed(mockActivity))

        verify(inverse = true) {
            mockBridge.lifecycleEvent(any())
        }
        verify(inverse = true) {
            mockWebViewClient.destroyWebView()
        }
    }

    @Test
    fun `app foregrounded injects foreground lifecycle event`() {
        observerSlot.captured(ActivityEvent.FirstStarted(mockActivity))

        verify {
            mockBridge.lifecycleEvent(
                JsBridge.LifecycleEventType.foreground
            )
        }
        verify(inverse = true) { mockWebViewClient.destroyWebView() }
        verify(inverse = true) { mockWebViewClient.initializeWebView() }
    }

    @Test
    fun `app backgrounded injects background lifecycle event`() {
        observerSlot.captured(ActivityEvent.AllStopped())

        verify {
            mockBridge.lifecycleEvent(
                JsBridge.LifecycleEventType.background
            )
        }
    }

    @Test
    fun `app foregrounded within session timeout injects foreground lifecycle event`() {
        observerSlot.captured(ActivityEvent.AllStopped())
        staticClock.execute(9_999)
        observerSlot.captured(ActivityEvent.FirstStarted(mockActivity))

        verify {
            mockBridge.lifecycleEvent(
                JsBridge.LifecycleEventType.foreground
            )
        }
        verify(inverse = true) { mockWebViewClient.destroyWebView() }
    }

    @Test
    fun `app foregrounded after session timeout resets webview`() {
        observerSlot.captured(ActivityEvent.AllStopped())
        staticClock.execute(10_000)
        observerSlot.captured(ActivityEvent.FirstStarted(mockActivity))

        verify(inverse = true) {
            mockBridge.lifecycleEvent(
                JsBridge.LifecycleEventType.foreground
            )
        }
        verify { mockWebViewClient.destroyWebView() }
        verify { mockWebViewClient.initializeWebView() }
    }
}
