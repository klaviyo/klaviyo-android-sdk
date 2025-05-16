package com.klaviyo.forms.bridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.webkit.WebMessageCompat
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.analytics.state.State
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * @see KlaviyoBridgeMessageHandler
 */
internal class KlaviyoBridgeMessageHandlerTest : BaseTest() {

    private val mockApiClient: ApiClient = mockk(relaxed = true)
    private val mockState: State = mockk(relaxed = true)
    private val mockWebViewClient: WebViewClient = mockk(relaxed = true)
    private val mockPresentationManager: PresentationManager = mockk(relaxed = true)

    private lateinit var bridgeMessageHandler: KlaviyoBridgeMessageHandler

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
        Registry.register<ApiClient>(mockApiClient)
        Registry.register<State>(mockState)
        Registry.register<WebViewClient>(mockWebViewClient)
        Registry.register<PresentationManager>(mockPresentationManager)

        bridgeMessageHandler = KlaviyoBridgeMessageHandler()
    }

    @After
    override fun cleanup() {
        unmockDeviceProperties()
        Registry.unregister<ApiClient>()
        Registry.unregister<State>()
        Registry.unregister<WebViewClient>()
        Registry.unregister<PresentationManager>()
        super.cleanup()
    }

    private fun postMessage(message: String) {
        bridgeMessageHandler.onPostMessage(
            mockk(relaxed = true),
            WebMessageCompat(message, null),
            mockk(relaxed = true),
            true,
            mockk(relaxed = true)
        )
    }

    @Test
    fun `jsReady triggers client onLocalJsReady`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler.jsReady
         */
        postMessage("""{"type":"jsReady"}""")
        verify { mockWebViewClient.onLocalJsReady() }
    }

    @Test
    fun `handShook triggers client onJsHandshakeCompleted`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler.handShook
         */
        postMessage("""{"type":"handShook"}""")
        verify { mockWebViewClient.onJsHandshakeCompleted() }
    }

    @Test
    fun `formWillAppear triggers show`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler.show
         */
        postMessage("""{"type":"formWillAppear"}""")
        verify { mockPresentationManager.present() }
    }

    @Test
    fun `trackAggregateEvent enqueues API request`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler.createAggregateEvent
         */
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
                """
                    {
                      "metric_group": "signup-forms",
                      "events": [
                        {
                          "log_to_metrics_service": true,
                          "metric": "stepSubmit",
                          "log_to_statsd": true,
                          "event_details": {
                            "page_url": "http://localhost:4001/onsite/js/",
                            "first_referrer": "http://localhost:4001/onsite/js/",
                            "action_type": "Submit Step",
                            "form_version_id": 3,
                            "form_id": "64CjgW",
                            "device_type": "DESKTOP",
                            "form_type": "POPUP",
                            "referrer": "http://localhost:4001/onsite/js/",
                            "submitted_fields": {
                              "sms_consent": true,
                              "consent_method": "Klaviyo Form",
                              "consent_form_version": 3,
                              "step_name": "Email Opt-In",
                              "consent_form_id": "64CjgW",
                              "source": "Local Form",
                              "email": "local@local.com",
                              "sent_identifiers": {}
                            },
                            "hostname": "localhost",
                            "step_number": 1,
                            "form_version_c_id": "1",
                            "step_name": "Email Opt-In",
                            "is_client": true,
                            "href": "http://localhost:4001/onsite/js/",
                            "cid": "ODZjYjJmMjUtNjliMC00ZGVlLTllM2YtNDY5YTlmNjcwYmUz"
                          },
                          "metric_service_event_name": "submitted_form_step",
                          "log_to_s3": true
                        }
                      ]
                    }
                """.trimIndent()
            )

        postMessage(aggregateMessage)
        val slot = slot<AggregateEventPayload>()
        verify { mockApiClient.enqueueAggregateEvent(capture(slot)) }
        assertEquals(expectedAggBody.toString(), slot.captured.toString())
    }

    @Test
    fun `trackProfileEvent enqueues API request`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler.createProfileEvent
         */
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

        postMessage(eventMessage)

        // required steps to the profile event
        verify { mockState.getAsProfile() }
        val slot = slot<Event>()
        verify { mockApiClient.enqueueEvent(capture(slot), any()) }
        assertEquals(expectedMetric, slot.captured.metric)
    }

    @Test
    fun `openDeepLink broadcasts intent to start activity`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler.deepLink
         */
        every { mockContext.startActivity(any()) } just runs
        every { mockContext.packageName } returns BuildConfig.LIBRARY_PACKAGE_NAME

        mockkStatic(Uri::class)
        val mockUrl = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUrl

        val mockActivity: Activity = mockk(relaxed = true)
        every { mockLifecycleMonitor.currentActivity } returns mockActivity

        val uriSlot = slot<Uri>()
        val actionSlot = slot<String>()
        val packageSlot = slot<String>()
        val flagsSlot = slot<Int>()

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setData(capture(uriSlot)) } returns mockk<Intent>()
        every { anyConstructed<Intent>().setAction(capture(actionSlot)) } returns mockk<Intent>()
        every { anyConstructed<Intent>().setPackage(capture(packageSlot)) } returns mockk<Intent>()
        every { anyConstructed<Intent>().setFlags(capture(flagsSlot)) } returns mockk<Intent>()

        val deeplinkMessage = """
            {
              "type": "openDeepLink",
              "data": {
                "ios": "klaviyotest://settings",
                "android": "klaviyotest://settings"
              }
            }
        """.trimIndent()

        postMessage(deeplinkMessage)

        verify { mockActivity.startActivity(any()) }

        assertEquals(mockUrl, uriSlot.captured)
        assertEquals("android.intent.action.VIEW", actionSlot.captured)
        assertEquals(BuildConfig.LIBRARY_PACKAGE_NAME, packageSlot.captured)
        assertEquals(0x20000000, flagsSlot.captured)
    }

    @Test
    fun `formDisappeared triggers close`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler.close
         */
        postMessage("""{"type":"formDisappeared"}""")
        verify { mockPresentationManager.dismiss() }
    }

    @Test
    fun `abort triggers closes`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler.abort
         */
        postMessage("""{"type":"abort"}""")
        verify { mockPresentationManager.dismiss() }
    }

    @Test
    fun `malformed message throws an error`() {
        postMessage("sawr a warewolf with a chinese menu inhis hands")

        verify {
            spyLog.error(
                "Failed to relay webview message: sawr a warewolf with a chinese menu inhis hands",
                any<JSONException>()
            )
        }
    }
}
