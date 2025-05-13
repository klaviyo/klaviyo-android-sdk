package com.klaviyo.forms.presentation

import android.content.res.Configuration
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Test

class KlaviyoPresentationManagerTest : BaseTest() {
    private val slotOnActivityEvent = slot<ActivityObserver>()
    private val mockWebViewClient = mockk<WebViewClient>(relaxed = true)

    override fun setup() {
        super.setup()
        Registry.register<WebViewClient>(mockWebViewClient)
    }

    override fun cleanup() {
        super.cleanup()
        Registry.unregister<WebViewClient>()
    }

    @Test
    fun `verify webview closes on an orientation change`() {
        every { mockLifecycleMonitor.onActivityEvent(capture(slotOnActivityEvent)) } just runs

        KlaviyoPresentationManager()

        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(Configuration()))
        // if we emit the same config change, we still should only close once
        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(Configuration()))

        verify(exactly = 1) { spyLog.debug("New screen orientation, closing form") }
        verify(exactly = 1) { mockWebViewClient.detachWebView(mockActivity) }
    }

    @Test
    fun `present should attach webview`() {
        KlaviyoPresentationManager().present()
        verify(exactly = 1) { mockWebViewClient.attachWebView(mockActivity) }
    }

    @Test
    fun `present should fail gracefully when activity is null`() {
        every { mockLifecycleMonitor.currentActivity } returns null
        KlaviyoPresentationManager().present()
        verify(exactly = 0) { mockWebViewClient.attachWebView(mockActivity) }
        verify { spyLog.warning("Unable to show IAF - null activity reference") }
    }

    @Test
    fun `dismiss should detach webview`() {
        KlaviyoPresentationManager().dismiss()
        verify(exactly = 1) { mockWebViewClient.detachWebView(mockActivity) }
    }

    @Test
    fun `dismiss should fail gracefully when activity is null`() {
        every { mockLifecycleMonitor.currentActivity } returns null
        KlaviyoPresentationManager().dismiss()
        verify(exactly = 0) { mockWebViewClient.detachWebView(mockActivity) }
        verify { spyLog.warning("Unable to dismiss IAF - null activity reference") }
    }
}
