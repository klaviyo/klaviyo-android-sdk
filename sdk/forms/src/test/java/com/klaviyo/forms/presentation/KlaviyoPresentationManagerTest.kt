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
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class KlaviyoPresentationManagerTest : BaseTest() {
    private val slotOnActivityEvent = slot<ActivityObserver>()
    private val mockWebViewClient = mockk<WebViewClient>(relaxed = true)
    private val mockOverlayActivity: Activity = mockk<KlaviyoFormsOverlayActivity>(relaxed = true)
    private val mockLaunchIntent = mockk<Intent>(relaxed = true)

    override fun setup() {
        super.setup()
        mockkObject(KlaviyoFormsOverlayActivity).apply {
            every { KlaviyoFormsOverlayActivity.launchIntent } returns mockLaunchIntent
        }

        every { mockLifecycleMonitor.onActivityEvent(capture(slotOnActivityEvent)) } just runs
        every { mockContext.startActivity(mockLaunchIntent) } just runs
        Registry.register<WebViewClient>(mockWebViewClient)
    }

    override fun cleanup() {
        super.cleanup()
        unmockkObject(KlaviyoFormsOverlayActivity)
        Registry.unregister<WebViewClient>()
    }

    private fun withPresentedState(): KlaviyoPresentationManager = KlaviyoPresentationManager().apply {
        assert(slotOnActivityEvent.isCaptured) { "Lifecycle listener should be captured" }
        slotOnActivityEvent.captured(ActivityEvent.Created(mockOverlayActivity, null))
    }

    private fun withHiddenState() = KlaviyoPresentationManager().apply {
        assertEquals(
            "PresentationState default to Hidden",
            PresentationState.Hidden,
            presentationState
        )
    }

    @Test
    fun `verify it attaches webview to the klaviyo activity`() {
        val manager = withPresentedState()

        verify(exactly = 1) { mockWebViewClient.attachWebView(mockOverlayActivity) }
        assertEquals(
            "PresentationState should be Presented after overlay activity is created",
            PresentationState.Presented(null),
            manager.presentationState
        )
    }

    @Test
    fun `verify it ignores non-klaviyo activities`() {
        val manager = withHiddenState()

        slotOnActivityEvent.captured(ActivityEvent.Created(mockActivity, null))

        verify(exactly = 0) { mockWebViewClient.attachWebView(mockOverlayActivity) }
        assertEquals(
            "PresentationState should remain Hidden if non-klaviyo activity is created",
            PresentationState.Hidden,
            manager.presentationState
        )
    }

    @Test
    fun `verify it ignores orientation change if not presenting`() {
        withHiddenState()

        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(mockk()))

        verifyRotationClose(0)
    }

    @Test
    fun `verify webview closes on an orientation change`() {
        withPresentedState()

        val mockConfig = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }

        // Initial orientation event must be a change
        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(mockConfig))
        verifyRotationClose(1)

        // Re-open it, and issue the same orientation again, which should be ignored
        slotOnActivityEvent.captured(ActivityEvent.Created(mockOverlayActivity, null))
        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(mockConfig))

        verifyRotationClose(1)

        // Issue a different orientation, which should again close the webview
        slotOnActivityEvent.captured(
            ActivityEvent.ConfigurationChanged(
                mockk<Configuration>(
                    relaxed = true
                ) {
                    orientation = Configuration.ORIENTATION_PORTRAIT
                }
            )
        )

        verifyRotationClose(2)
    }

    @Test
    fun `other lifecycle events are ignored`() {
        val spyManger = spyk(withPresentedState())

        slotOnActivityEvent.captured(ActivityEvent.Started(mockk()))
        slotOnActivityEvent.captured(ActivityEvent.Resumed(mockk()))
        slotOnActivityEvent.captured(ActivityEvent.SaveInstanceState(mockk(), mockk()))
        slotOnActivityEvent.captured(ActivityEvent.Paused(mockk()))
        slotOnActivityEvent.captured(ActivityEvent.Stopped(mockk()))
        slotOnActivityEvent.captured(ActivityEvent.AllStopped())

        verifyRotationClose(0)
    }

    private fun verifyRotationClose(callCount: Int) {
        verify(exactly = callCount) { spyLog.debug("New screen orientation, closing form") }
        verify(exactly = callCount) { mockOverlayActivity.finish() }
        verify(exactly = callCount) { mockWebViewClient.detachWebView(mockOverlayActivity) }
    }

    @Test
    fun `present should not start a duplicate activity`() {
        val manager = withPresentedState()
        manager.present("formId")
        verify(exactly = 0) { mockContext.startActivity(mockLaunchIntent) }
        verify { spyLog.debug("Cannot present activity, currently in state: Presented(formId=null)") }
    }

    @Test
    fun `dismiss should detach webview and finish activity`() {
        val manager = withPresentedState()
        manager.dismiss()
        verify(exactly = 1) { mockWebViewClient.detachWebView(mockOverlayActivity) }
        verify(exactly = 1) { mockOverlayActivity.finish() }
        assertEquals(
            "PresentationState should reset to Hidden",
            PresentationState.Hidden,
            manager.presentationState
        )
    }

    @Test
    fun `dismiss should be ignored if not currently presenting`() {
        val manager = withHiddenState()
        manager.dismiss()
        verify(exactly = 0) { mockWebViewClient.detachWebView(mockOverlayActivity) }
        verify(exactly = 0) { mockOverlayActivity.finish() }
        verify { spyLog.debug("No-op dismiss: overlay activity is not presented") }
    }

    @Test
    fun `test full presentation cycle`() {
        val manager = withHiddenState()

        // Present comes first
        manager.present("formId")

        // Expect start activity to be called and state to be Presenting
        verify(exactly = 1) { mockContext.startActivity(mockLaunchIntent) }
        assertEquals(
            "PresentationState should transition to Presenting",
            PresentationState.Presenting("formId"),
            manager.presentationState
        )

        // Simulate activity creation lifecycle event
        slotOnActivityEvent.captured(ActivityEvent.Created(mockOverlayActivity, null))

        verify(exactly = 1) { mockWebViewClient.attachWebView(mockOverlayActivity) }
        assertEquals(
            "PresentationState should transition to Presented",
            PresentationState.Presented("formId"),
            manager.presentationState
        )

        manager.dismiss()
        verify(exactly = 1) { mockWebViewClient.detachWebView(mockOverlayActivity) }
        verify(exactly = 1) { mockOverlayActivity.finish() }
        assertEquals(
            "PresentationState should reset to Hidden",
            PresentationState.Hidden,
            manager.presentationState
        )
    }
}
