package com.klaviyo.forms

import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class KlaviyoWebViewDelegateTest : BaseTest() {

    private companion object {
        private val slotOnActivityEvent = slot<ActivityObserver>()
    }

    private val mockApiClient: ApiClient = mockk(relaxed = true)
    private val mockState: State = mockk(relaxed = true)
    private val mockSettings: WebSettings = mockk(relaxed = true)
    private val mockParentView: ViewGroup = mockk(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
        Registry.register<ApiClient>(mockApiClient)
        Registry.register<State>(mockState)
        every { mockConfig.isDebugBuild } returns false
        mockkConstructor(KlaviyoWebView::class)

        every { anyConstructed<KlaviyoWebView>().settings } returns mockSettings
        every { anyConstructed<KlaviyoWebView>().setBackgroundColor(any()) } just runs
        every { anyConstructed<KlaviyoWebView>().webViewClient = any() } just runs
        every { anyConstructed<KlaviyoWebView>().visibility = any() } just runs
        every { anyConstructed<KlaviyoWebView>().parent } returns mockParentView
        every { anyConstructed<KlaviyoWebView>().destroy() } just runs
        every {
            anyConstructed<KlaviyoWebView>().loadDataWithBaseURL(
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>()
            )
        } just runs

        mockkStatic(WebViewFeature::class)
        every { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) } returns true

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        mockkStatic(WebViewCompat::class)
        every { WebViewCompat.addWebMessageListener(any(), any(), any(), any()) } just runs

        every { mockLifecycleMonitor.onActivityEvent(capture(slotOnActivityEvent)) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
        unmockkConstructor(KlaviyoWebView::class)
    }

    private fun verifyClose(doesNotClose: Boolean = false) {
        val times = if (doesNotClose) 0 else 1
        val slot = slot<Runnable>()
        verify(exactly = times) { mockDecorView.post(capture(slot)) }
        if (!doesNotClose) slot.captured.run()
        verify(exactly = times) { spyLog.verbose("Clear IAF WebView reference") }
        verify(exactly = times) { mockParentView.removeView(any()) }
        verify(exactly = times) { anyConstructed<KlaviyoWebView>().destroy() }
        assertEquals(staticClock.scheduledTasks.size, 0) // timer is cancelled
    }

    private fun verifyShow(doesNotShow: Boolean = false) {
        val times = if (doesNotShow) 0 else 1
        val slot = slot<Runnable>()
        verify(exactly = times) { mockDecorView.post(capture(slot)) }
        if (!doesNotShow) slot.captured.run()
        verify(exactly = times) { anyConstructed<KlaviyoWebView>().visibility = View.VISIBLE }
        verify(exactly = times) { mockDecorView.findViewById<ViewGroup>(any()) }
        verify(exactly = times) { mockContentView.addView(any()) }
    }

    @Test
    fun `initializeWebView triggers loadTemplate`() {
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        every {
            anyConstructed<KlaviyoWebView>()
                .loadTemplate(any(), delegate)
        } returns Unit
        // checks we load and call these config values
        verify { anyConstructed<KlaviyoWebView>().loadTemplate(any(), delegate) }
        verify { mockAssets.open("InAppFormsTemplate.html") }
        verify { mockConfig.sdkName }
        verify { mockConfig.sdkVersion }
        // tells us timer has started
        assertEquals(staticClock.scheduledTasks.size, 1)
    }

    @Test
    fun `show message type success case`() {
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage("""{"type":"formWillAppear"}""")

        verifyShow()
    }

    @Test
    fun `malformed message throws an error`() {
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage("""sawr a warewolf with a chinese menu inhis hands""")

        verify {
            spyLog.warning(
                "Failed to relay webview message: sawr a warewolf with a chinese menu inhis hands",
                any<JSONException>()
            )
        }
    }

    @Test
    fun `deeplink but we have a null activity`() {
        every { mockLifecycleMonitor.currentActivity } returns null

        val deeplinkMessage = """
            {
              "type": "openDeepLink",
              "data": {
                "ios": "klaviyotest://settings",
                "android": "klaviyotest://settings"
              }
            }
        """.trimIndent()
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage(deeplinkMessage)

        verify {
            spyLog.warning(
                "Failed to launch deeplink klaviyotest://settings - null activity reference"
            )
        }
    }

    @Test
    fun `appends asset source`() {
        every { mockConfig.assetSource } returns "riders-on-the-stromboli"

        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()

        verify { spyLog.debug("Appending assetSource=riders-on-the-stromboli to klaviyo.js") }
    }

    @Test
    fun `show message type null webview does not show`() {
        val delegate = KlaviyoWebViewDelegate()
        // notably do not init webview
        delegate.postMessage("""{"type":"formWillAppear"}""")

        verify { spyLog.warning("Unable to show IAF - null WebView reference") }
        verifyShow(doesNotShow = true)
    }

    @Test
    fun `show message type null activity`() {
        every { mockActivity.window?.decorView } returns null

        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage("""{"type":"formWillAppear"}""")

        verify { spyLog.warning("Unable to show IAF - null activity reference") }
        verifyShow(doesNotShow = true)
    }

    @Test
    fun `settings are properly set`() {
        every {
            anyConstructed<KlaviyoWebView>()
                .settings
        } returns mockSettings
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()

        verify { mockSettings.javaScriptEnabled = true }
        verify { mockSettings.userAgentString = "Mock User Agent" }
        verify { mockSettings.domStorageEnabled = true }
    }

    @Test
    fun `postMessage handles handshake correctly`() {
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()

        delegate.postMessage("""{"type":"handShook"}""")
        staticClock.execute(10_000)

        verifyClose(doesNotClose = true)
    }

    @Test
    fun `closes webview on timer exhaust`() {
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        // notably no message received
        staticClock.execute(10_000)

        verify { spyLog.debug("IAF WebView Aborted: Timeout waiting for Klaviyo.js") }
        verifyClose()
    }

    @Test
    fun `postMessage handles close correctly`() {
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage("""{"type":"formDisappeared"}""")

        verifyClose()
    }

    @Test
    fun `abort messages properly closes`() {
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage("""{"type":"abort"}""")

        verify { spyLog.info("IAF aborted, reason: Unknown") }
        verifyClose()
    }

    @Test
    fun `create profile event handled properly`() {
        val eventMessage = """
           {
              "type": "trackProfileEvent",
              "data": {
                "metric": "Form completed by profile",
                "properties": {}
              }
           } 
        """.trimIndent()
        val expectedMetric = EventMetric.CUSTOM("Form completed by profile")

        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage(eventMessage)

        // required steps to the profile event
        verify { mockState.getAsProfile() }
        val slot = slot<Event>()
        verify { mockApiClient.enqueueEvent(capture(slot), any()) }
        assertEquals(slot.captured.metric, expectedMetric)
    }

    @Test
    fun `verify webview closes on an orientation change`() {
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()

        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(Configuration()))
        // if we emit the same config change we still should only close once
        slotOnActivityEvent.captured(ActivityEvent.ConfigurationChanged(Configuration()))

        verify(exactly = 1) { spyLog.debug("New screen orientation, closing form") }
        verifyClose()
    }

    @Test
    fun `verify close fails on a null webview`() {
        val delegate = KlaviyoWebViewDelegate()
        // notably do not init webview
        delegate.postMessage("""{"type":"formDisappeared"}""")
        verify { spyLog.warning("Unable to close IAF - null WebView reference") }
        verifyClose(true)
    }

    @Test
    fun `verify close fails on a null decorview`() {
        every { mockActivity.window?.decorView } returns null

        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage("""{"type":"formDisappeared"}""")
        verify { spyLog.warning("Unable to close IAF - null activity reference") }
        verifyClose(true)
    }

    @Test
    fun `postMessage handles AggregateEventTracked`() {
        val aggregateMessage = """
            {
              "type": "trackAggregateEvent",
              "data": {
                "metric_group": "signup-forms",
                "events": [
                  {
                    "metric": "stepSubmit",
                    "log_to_statsd": true,
                    "log_to_s3": true,
                    "log_to_metrics_service": true,
                    "metric_service_event_name": "submitted_form_step",
                    "event_details": {
                      "form_version_c_id": "1",
                      "is_client": true,
                      "submitted_fields": {
                        "source": "Local Form",
                        "email": "local@local.com",
                        "consent_method": "Klaviyo Form",
                        "consent_form_id": "64CjgW",
                        "consent_form_version": 3,
                        "sent_identifiers": {},
                        "sms_consent": true,
                        "step_name": "Email Opt-In"
                      },
                      "step_name": "Email Opt-In",
                      "step_number": 1,
                      "action_type": "Submit Step",
                      "form_id": "64CjgW",
                      "form_version_id": 3,
                      "form_type": "POPUP",
                      "device_type": "DESKTOP",
                      "hostname": "localhost",
                      "href": "http://localhost:4001/onsite/js/",
                      "page_url": "http://localhost:4001/onsite/js/",
                      "first_referrer": "http://localhost:4001/onsite/js/",
                      "referrer": "http://localhost:4001/onsite/js/",
                      "cid": "ODZjYjJmMjUtNjliMC00ZGVlLTllM2YtNDY5YTlmNjcwYmUz"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val expectedAggBody =
            JSONObject(
                "{\"metric_group\":\"signup-forms\",\"events\":[{\"log_to_metrics_service\":true,\"metric\":\"stepSubmit\",\"log_to_statsd\":true,\"event_details\":{\"page_url\":\"http://localhost:4001/onsite/js/\",\"first_referrer\":\"http://localhost:4001/onsite/js/\",\"action_type\":\"Submit Step\",\"form_version_id\":3,\"form_id\":\"64CjgW\",\"device_type\":\"DESKTOP\",\"form_type\":\"POPUP\",\"referrer\":\"http://localhost:4001/onsite/js/\",\"submitted_fields\":{\"sms_consent\":true,\"consent_method\":\"Klaviyo Form\",\"consent_form_version\":3,\"step_name\":\"Email Opt-In\",\"consent_form_id\":\"64CjgW\",\"source\":\"Local Form\",\"email\":\"local@local.com\",\"sent_identifiers\":{}},\"hostname\":\"localhost\",\"step_number\":1,\"form_version_c_id\":\"1\",\"step_name\":\"Email Opt-In\",\"is_client\":true,\"href\":\"http://localhost:4001/onsite/js/\",\"cid\":\"ODZjYjJmMjUtNjliMC00ZGVlLTllM2YtNDY5YTlmNjcwYmUz\"},\"metric_service_event_name\":\"submitted_form_step\",\"log_to_s3\":true}]}"
            )
        val delegate = KlaviyoWebViewDelegate()
        delegate.initializeWebView()
        delegate.postMessage(aggregateMessage)
        val slot = slot<AggregateEventPayload>()
        verify { mockApiClient.enqueueAggregateEvent(capture(slot)) }
        assertEquals(slot.captured.toString(), expectedAggBody.toString())
    }
}
