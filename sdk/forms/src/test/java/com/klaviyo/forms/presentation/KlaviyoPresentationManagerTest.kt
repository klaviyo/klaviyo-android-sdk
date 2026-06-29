package com.klaviyo.forms.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.FormLifecycleEvent
import com.klaviyo.forms.FormLifecycleHandler
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.bridge.FormLayout
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class KlaviyoPresentationManagerTest : BaseTest() {
    /**
     * Tracks every observer the manager registers via [LifecycleMonitor.onActivityEvent].
     * The manager registers the primary observer from its `init` block and may register
     * additional one-shot observers for rotation / device-info push. Tests dispatch events
     * to all currently-registered observers via [dispatchEvent], mirroring how the real
     * [LifecycleMonitor] broadcasts events to every registered observer.
     */
    private val registeredObservers = mutableListOf<ActivityObserver>()

    /**
     * Convenience slot for legacy tests that only care about the most recently registered
     * observer. Prefer [dispatchEvent] for new tests that exercise observer interleaving.
     */
    private val slotOnActivityEvent = slot<ActivityObserver>()
    private val mockWebViewClient = mockk<WebViewClient>(relaxed = true)
    private val mockOverlayActivity: Activity = mockk<KlaviyoFormsOverlayActivity>(relaxed = true)
    private val mockLaunchIntent = mockk<Intent>(relaxed = true)

    private fun dispatchEvent(event: ActivityEvent) {
        // Iterate over a snapshot — observers may register/unregister others during dispatch
        registeredObservers.toList().forEach { it(event) }
    }

    override fun setup() {
        super.setup()
        mockkObject(KlaviyoFormsOverlayActivity).apply {
            every { KlaviyoFormsOverlayActivity.launchIntent } returns mockLaunchIntent
        }
        every { mockLifecycleMonitor.onActivityEvent(capture(slotOnActivityEvent)) } answers {
            registeredObservers.add(slotOnActivityEvent.captured)
        }
        every { mockLifecycleMonitor.offActivityEvent(any()) } answers {
            registeredObservers.remove(firstArg<ActivityObserver>())
        }
        every { mockContext.startActivity(mockLaunchIntent) } just runs
        Registry.register<WebViewClient>(mockWebViewClient)
        Registry.register<InAppFormsConfig>(InAppFormsConfig())
    }

    override fun cleanup() {
        super.cleanup()
        unmockkObject(KlaviyoFormsOverlayActivity)
        Registry.unregister<WebViewClient>()
        Registry.unregister<JsBridge>()
        Registry.unregister<InAppFormsConfig>()
    }

    private fun withPresentedState(): KlaviyoPresentationManager = KlaviyoPresentationManager().mockPresent()

    private fun KlaviyoPresentationManager.mockPresent() = apply {
        present(null)
        assert(slotOnActivityEvent.isCaptured) { "Lifecycle listener should be captured" }
        dispatchEvent(ActivityEvent.Created(mockOverlayActivity, null))
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

        dispatchEvent(ActivityEvent.Created(mockActivity, null))

        verify(exactly = 0) { mockWebViewClient.attachWebView(mockOverlayActivity) }
        assertEquals(
            "PresentationState should remain Hidden if non-klaviyo activity is created",
            PresentationState.Hidden,
            manager.presentationState
        )
    }

    @Test
    fun `verify webview re-attaches during orientation change`() {
        withPresentedState()

        val mockConfig = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }

        // Initial orientation event must be a change
        dispatchEvent(ActivityEvent.ConfigurationChanged(mockConfig))
        verify { mockWebViewClient.detachWebView() }

        // After configuration change, the activity gets re-created,
        // at which point we should re-attach if we were previously presenting
        dispatchEvent(ActivityEvent.Created(mockOverlayActivity, mockk()))
        verify { mockWebViewClient.attachWebView(mockOverlayActivity) }
    }

    @Test
    fun `pushDeviceInfo is deferred until next Resumed activity after orientation change`() {
        withPresentedState()

        val mockConfig = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }

        // ConfigurationChanged fires while the stale activity is still current —
        // pushDeviceInfo must NOT be called yet, or it would capture pre-rotation
        // Display.rotation/rootWindowInsets values.
        dispatchEvent(ActivityEvent.ConfigurationChanged(mockConfig))
        verify(exactly = 0) { mockWebViewClient.pushDeviceInfo() }

        // The next Resumed activity represents the rotated activity with fresh
        // Display metrics — pushDeviceInfo should fire exactly once here.
        dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
        verify(exactly = 1) { mockWebViewClient.pushDeviceInfo() }

        // Subsequent Resumed events (e.g. backgrounding/foregrounding after rotation)
        // must NOT re-trigger pushDeviceInfo — the one-shot observer is consumed.
        dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
        dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
        verify(exactly = 1) { mockWebViewClient.pushDeviceInfo() }
    }

    @Test
    fun `pushDeviceInfo fires on rotation even when no form is presenting`() {
        // Preloaded-but-not-presenting webview case: no present() was called.
        KlaviyoPresentationManager()

        val mockConfig = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }

        dispatchEvent(ActivityEvent.ConfigurationChanged(mockConfig))
        verify(exactly = 0) { mockWebViewClient.pushDeviceInfo() }

        dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
        verify(exactly = 1) { mockWebViewClient.pushDeviceInfo() }
    }

    @Test
    fun `pushDeviceInfo is not triggered by non-orientation configuration changes`() {
        withPresentedState()

        // Same orientation — e.g. locale change, dark-mode toggle, font scale update.
        // The manager's initial orientation is null, so the first ConfigurationChanged
        // with any orientation value is treated as a change. Establish a baseline first.
        val portrait = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
        dispatchEvent(ActivityEvent.ConfigurationChanged(portrait))
        // Baseline push after first orientation is observed
        dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
        verify(exactly = 1) { mockWebViewClient.pushDeviceInfo() }

        // Now a non-orientation config change (same PORTRAIT) — must not schedule
        // or fire another device-info push.
        val portraitAgain = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
        dispatchEvent(ActivityEvent.ConfigurationChanged(portraitAgain))
        dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
        verify(exactly = 1) { mockWebViewClient.pushDeviceInfo() }
    }

    @Test
    fun `pushDeviceInfo observer times out if no Resumed ever arrives`() {
        withPresentedState()

        val mockConfig = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        dispatchEvent(ActivityEvent.ConfigurationChanged(mockConfig))

        // Advance well past the safety timeout — observer should have unregistered itself.
        staticClock.execute(10_000L)

        // Late Resumed arrives after the observer timed out — must not fire pushDeviceInfo.
        dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
        verify(exactly = 0) { mockWebViewClient.pushDeviceInfo() }
    }

    @Test
    fun `rapid rotation replaces prior pushDeviceInfo observer so only latest fires`() {
        withPresentedState()

        val landscape = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        val portrait = mockk<Configuration>(relaxed = true) {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }

        // Two rotations land before the next Resumed.
        dispatchEvent(ActivityEvent.ConfigurationChanged(landscape))
        dispatchEvent(ActivityEvent.ConfigurationChanged(portrait))

        dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
        // Should only push once — the second ConfigurationChanged should have dropped the
        // first one-shot observer, so only the latest fires on Resumed.
        verify(exactly = 1) { mockWebViewClient.pushDeviceInfo() }
    }

    @Test
    fun `other lifecycle events are ignored for activity-based forms`() {
        withPresentedState()
        verify(exactly = 1) { mockWebViewClient.attachWebView(mockOverlayActivity) }

        dispatchEvent(ActivityEvent.Started(mockk()))
        dispatchEvent(ActivityEvent.Resumed(mockk()))
        dispatchEvent(ActivityEvent.SaveInstanceState(mockk(), mockk()))
        dispatchEvent(ActivityEvent.Paused(mockk()))
        dispatchEvent(ActivityEvent.Stopped(mockk()))
        dispatchEvent(ActivityEvent.AllStopped())

        verify(inverse = true) { mockWebViewClient.detachWebView() }
        verify(exactly = 1) { mockWebViewClient.attachWebView(mockOverlayActivity) }
    }

    @Test
    fun `present should not start a duplicate activity`() {
        val manager = withPresentedState()
        verify(exactly = 1) { mockActivity.startActivity(mockLaunchIntent) }
        manager.present(null)
        verify(exactly = 1) { mockActivity.startActivity(mockLaunchIntent) }
    }

    @Test
    fun `dismiss should detach webview and finish activity`() {
        val manager = withPresentedState()
        manager.dismiss()
        verify(exactly = 1) { mockWebViewClient.detachWebView() }
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
        verify(exactly = 0) { mockWebViewClient.detachWebView() }
        verify(exactly = 0) { mockOverlayActivity.finish() }
        verify { spyLog.debug(any()) }
    }

    @Test
    fun `test full presentation cycle`() {
        val manager = withHiddenState()

        // Present comes first
        manager.present(null)

        // Expect start activity to be called and state to be Presenting
        verify(exactly = 1) { mockActivity.startActivity(mockLaunchIntent) }
        assertEquals(
            "PresentationState should transition to Presenting",
            PresentationState.Presenting(null),
            manager.presentationState
        )

        // Simulate activity creation lifecycle event
        dispatchEvent(ActivityEvent.Created(mockOverlayActivity, null))

        verify(exactly = 1) { mockWebViewClient.attachWebView(mockOverlayActivity) }
        assertEquals(
            "PresentationState should transition to Presented",
            PresentationState.Presented(null),
            manager.presentationState
        )

        manager.dismiss()
        verify(exactly = 1) { mockWebViewClient.detachWebView() }
        verify(exactly = 1) { mockOverlayActivity.finish() }
        assertEquals(
            "PresentationState should reset to Hidden",
            PresentationState.Hidden,
            manager.presentationState
        )
    }

    @Test
    fun `test closeFormAndDismiss with expected JS callback`() {
        val manager = withPresentedState()
        val mockBridge = mockk<JsBridge>().apply {
            every { closeForm() } answers {
                // Simulate expected JS behavior of sending back a form disappeared message
                manager.dismiss()
            }
        }
        Registry.register<JsBridge>(mockBridge)

        manager.closeFormAndDismiss()

        verify(exactly = 1) { mockBridge.closeForm() }
        verify(exactly = 1) { mockWebViewClient.detachWebView() }
        verify(exactly = 1) { mockOverlayActivity.finish() }
        assertEquals(
            PresentationState.Hidden,
            manager.presentationState
        )
    }

    @Test
    fun `test closeFormAndDismiss dismisses if closeForm does not call back in time`() {
        val manager = withPresentedState()
        val mockBridge = mockk<JsBridge>().apply {
            every { closeForm() } just runs
        }
        Registry.register<JsBridge>(mockBridge)

        manager.closeFormAndDismiss()

        verify(exactly = 1) { mockBridge.closeForm() }
        verify(exactly = 0) { mockWebViewClient.detachWebView() }
        verify(exactly = 0) { mockOverlayActivity.finish() }

        staticClock.execute(600L)

        verify(exactly = 1) { mockWebViewClient.detachWebView() }
        verify(exactly = 1) { mockOverlayActivity.finish() }
        assertEquals(
            PresentationState.Hidden,
            manager.presentationState
        )
    }

    @Test
    fun `presentation manager does not fire lifecycle callbacks`() {
        val events = mutableListOf<FormLifecycleEvent>()
        val callback = FormLifecycleHandler { event -> events.add(event) }
        Registry.register<FormLifecycleHandler>(callback)

        val manager = withPresentedState()

        // PM should not fire FormShown — that's the bridge's job now
        assertEquals(0, events.size)

        manager.dismiss()

        // PM should not fire FormDismissed either
        assertEquals(0, events.size)

        Registry.unregister<FormLifecycleHandler>()
    }

    // ---- Floating Window Tests ----

    private val mockWebView = mockk<View>(relaxed = true)
    private val mockFloatingLayout = mockk<FormLayout>(relaxed = true).apply {
        every { isFullscreen } returns false
    }
    private val mockWindowManager = mockk<WindowManager>(relaxed = true)
    private val mockHostActivity = mockk<Activity>(relaxed = true).apply {
        every { getSystemService(Context.WINDOW_SERVICE) } returns mockWindowManager
    }

    /**
     * Sets up FloatingFormWindow constructor mocking and presents a floating form.
     * Must call [cleanupFloatingMocks] in the test or after block.
     */
    private fun withFloatingPresentedState(): KlaviyoPresentationManager {
        mockkConstructor(FloatingFormWindow::class)
        every { anyConstructed<FloatingFormWindow>().show(any(), any(), any(), any(), any()) } answers {
            // Invoke the onPresented callback (4th arg) to mirror real addView behavior
            arg<(() -> Unit)?>(3)?.invoke()
        }
        every { anyConstructed<FloatingFormWindow>().dismiss() } just runs
        every { mockWebViewClient.getWebView() } returns mockWebView

        // runWithCurrentOrNextActivity must return our host activity for floating window tests
        val slotJob = slot<(activity: Activity) -> Unit>()
        every {
            mockLifecycleMonitor.runWithCurrentOrNextActivity(any(), capture(slotJob))
        } answers {
            slotJob.captured.invoke(mockHostActivity)
            null
        }

        val manager = KlaviyoPresentationManager()
        manager.present("floatingFormId", mockFloatingLayout)

        assertEquals(
            "PresentationState should be Presented for floating window",
            PresentationState.Presented("floatingFormId"),
            manager.presentationState
        )
        return manager
    }

    private fun cleanupFloatingMocks() {
        unmockkConstructor(FloatingFormWindow::class)
    }

    @Test
    fun `floating window present and dismiss cycle`() {
        val manager = withFloatingPresentedState()
        try {
            verify {
                anyConstructed<FloatingFormWindow>().show(
                    any(),
                    mockWebView,
                    mockFloatingLayout,
                    any(),
                    any()
                )
            }

            manager.dismiss()

            verify { mockWebViewClient.detachWebView() }
            verify { anyConstructed<FloatingFormWindow>().dismiss() }
            assertEquals(PresentationState.Hidden, manager.presentationState)
        } finally {
            cleanupFloatingMocks()
        }
    }

    @Test
    fun `floating window host activity stopped triggers cleanup after grace period`() {
        val manager = withFloatingPresentedState()
        try {
            // Simulate the host activity stopping (multi-activity transition)
            dispatchEvent(ActivityEvent.Stopped(mockHostActivity))

            // Before grace period: still presented
            assertEquals(PresentationState.Presented("floatingFormId"), manager.presentationState)

            // After grace period: cleaned up
            staticClock.execute(LifecycleMonitor.ACTIVITY_TRANSITION_GRACE_PERIOD)

            verify { mockWebViewClient.detachWebView() }
            verify { anyConstructed<FloatingFormWindow>().dismiss() }
            assertEquals(PresentationState.Hidden, manager.presentationState)
        } finally {
            cleanupFloatingMocks()
        }
    }

    @Test
    fun `floating window stopped for non-host activity is ignored`() {
        val manager = withFloatingPresentedState()
        try {
            val otherActivity = mockk<Activity>(relaxed = true)
            dispatchEvent(ActivityEvent.Stopped(otherActivity))

            staticClock.execute(LifecycleMonitor.ACTIVITY_TRANSITION_GRACE_PERIOD)

            // Should still be presented — the stopped activity wasn't the host
            assertEquals(PresentationState.Presented("floatingFormId"), manager.presentationState)
        } finally {
            cleanupFloatingMocks()
        }
    }

    @Test
    fun `floating window app backgrounding cancels per-activity cleanup and re-presents`() {
        val manager = withFloatingPresentedState()
        try {
            // Simulate backgrounding: Stopped then AllStopped in quick succession
            dispatchEvent(ActivityEvent.Stopped(mockHostActivity))
            dispatchEvent(ActivityEvent.AllStopped())

            // AllStopped should cancel the per-activity cleanup and set Presenting for re-presentation
            assertEquals(PresentationState.Presenting("floatingFormId"), manager.presentationState)

            // Grace period expires — should NOT trigger the multi-activity cleanup
            // because AllStopped already cancelled it
            staticClock.execute(LifecycleMonitor.ACTIVITY_TRANSITION_GRACE_PERIOD)
            assertEquals(
                "State should remain Presenting after grace period since AllStopped handled it",
                PresentationState.Presenting("floatingFormId"),
                manager.presentationState
            )
        } finally {
            cleanupFloatingMocks()
        }
    }

    @Test
    fun `floating window dismiss during rotation cancels re-presentation observer`() {
        val manager = withFloatingPresentedState()
        try {
            val mockConfig = mockk<Configuration>(relaxed = true) {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }

            // Trigger rotation — this dismisses the window, sets floatingFormWindow = null,
            // and registers a rotation observer for re-presentation
            dispatchEvent(ActivityEvent.ConfigurationChanged(mockConfig))
            assertEquals(PresentationState.Presenting("floatingFormId"), manager.presentationState)

            // Dismiss cancels the rotation observer via clearTimers() and resets state
            manager.dismiss()
            assertEquals(
                "Dismiss during rotation should reset state to Hidden",
                PresentationState.Hidden,
                manager.presentationState
            )

            // Simulate new activity resuming — should NOT re-present the floating window
            // because the rotation observer was cleaned up by dismiss/clearTimers
            dispatchEvent(ActivityEvent.Resumed(mockk(relaxed = true)))
            assertEquals(
                "Resumed should not change state after dismiss cancels rotation observer",
                PresentationState.Hidden,
                manager.presentationState
            )
        } finally {
            cleanupFloatingMocks()
        }
    }

    @Test
    fun `floating window background timeout resets state to Hidden`() {
        val manager = withFloatingPresentedState()
        try {
            // Background the app
            dispatchEvent(ActivityEvent.Stopped(mockHostActivity))
            dispatchEvent(ActivityEvent.AllStopped())
            assertEquals(PresentationState.Presenting("floatingFormId"), manager.presentationState)

            // Simulate session timeout (default from InAppFormsConfig)
            val sessionTimeout = InAppFormsConfig()
                .getSessionTimeoutDuration().inWholeMilliseconds
            staticClock.execute(sessionTimeout)

            assertEquals(
                "State should reset to Hidden after session timeout",
                PresentationState.Hidden,
                manager.presentationState
            )
        } finally {
            cleanupFloatingMocks()
        }
    }

    @Test
    fun `floating window present with null webview falls back to Hidden`() {
        mockkConstructor(FloatingFormWindow::class)
        try {
            every { mockWebViewClient.getWebView() } returns null

            val manager = KlaviyoPresentationManager()
            manager.present("formId", mockFloatingLayout)

            assertEquals(
                "State should be Hidden when WebView is null",
                PresentationState.Hidden,
                manager.presentationState
            )
            verify { spyLog.warning(any()) }
        } finally {
            cleanupFloatingMocks()
        }
    }
}
