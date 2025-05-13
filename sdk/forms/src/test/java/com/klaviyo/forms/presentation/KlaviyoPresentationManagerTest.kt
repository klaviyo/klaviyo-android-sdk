package com.klaviyo.forms.presentation

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Test

class KlaviyoPresentationManagerTest : BaseTest() {
    private val slotOnActivityEvent = slot<ActivityObserver>()
    private val mockWebViewClient = mockk<WebViewClient>(relaxed = true)
    private val mockOverlayActivity: Activity = mockk<KlaviyoFormsOverlayActivity>(relaxed = true).apply {
        every { mockLifecycleMonitor.currentActivity } returns this
    }

    override fun setup() {
        super.setup()
        mockkObject(KlaviyoFormsOverlayActivity)
        every { mockContext.startActivity(any()) } just runs
        Registry.register<WebViewClient>(mockWebViewClient)
    }

    override fun cleanup() {
        super.cleanup()
        unmockkObject(KlaviyoFormsOverlayActivity)
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
        verify(exactly = 1) { mockOverlayActivity.finish() }
    }

    @Test
    fun `present should attach webview`() {
        val mockIntent = mockk<Intent>(relaxed = true)
        every { KlaviyoFormsOverlayActivity.launchIntent } returns mockIntent

        every { mockLifecycleMonitor.currentActivity } returns mockActivity
        KlaviyoPresentationManager().present()
        verify(exactly = 1) { mockContext.startActivity(any()) }
    }

    @Test
    fun `present should not start a duplicate activity`() {
        KlaviyoPresentationManager().present()
        verify(exactly = 0) { mockContext.startActivity(any()) }
        verify { spyLog.debug("Form Overlay Activity is already presented") }
    }

    @Test
    fun `dismiss should detach webview`() {
        KlaviyoPresentationManager().dismiss()
        verify(exactly = 1) { mockOverlayActivity.finish() }
    }

    @Test
    fun `dismiss should fail gracefully when activity is null`() {
        every { mockLifecycleMonitor.currentActivity } returns mockActivity
        verify(exactly = 0) { mockActivity.finish() }
    }
}
