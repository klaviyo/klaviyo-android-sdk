package com.klaviyo.forms.bridge

import android.net.Uri
import androidx.webkit.WebMessageCompat
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.core.config.DeepLinking
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.unregisterFromInAppForms
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * @see KlaviyoNativeBridge
 */
internal class KlaviyoNativeBridgeTest : BaseTest() {

    private val mockApiClient: ApiClient = mockk(relaxed = true)
    private val mockState: State = mockk(relaxed = true)
    private val mockWebViewClient: WebViewClient = mockk(relaxed = true)
    private val mockPresentationManager: PresentationManager = mockk(relaxed = true)

    private val mockUri = mockk<Uri>(relaxed = true)

    private lateinit var bridgeMessageHandler: KlaviyoNativeBridge

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
        mockkStatic("com.klaviyo.forms.InAppFormsKt") // Mock the extension function
        Registry.register<ApiClient>(mockApiClient)
        Registry.register<State>(mockState)
        Registry.register<WebViewClient>(mockWebViewClient)
        Registry.register<PresentationManager>(mockPresentationManager)

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockUri

        bridgeMessageHandler = KlaviyoNativeBridge()
    }

    @After
    override fun cleanup() {
        unmockDeviceProperties()
        unmockkAll()
        Registry.unregister<ApiClient>()
        Registry.unregister<State>()
        Registry.unregister<WebViewClient>()
        Registry.unregister<PresentationManager>()
        super.cleanup()
    }

    private fun postMessage(message: String?) {
        bridgeMessageHandler.onPostMessage(
            mockk(relaxed = true),
            WebMessageCompat(message, null),
            mockk(relaxed = true),
            true,
            mockk(relaxed = true)
        )
    }

    @Test
    fun `allowed origin returns the base URL`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.allowedOrigin
         */
        val expected = setOf(Registry.config.baseUrl)
        assertEquals(expected, bridgeMessageHandler.allowedOrigin)
    }

    @Test
    fun `jsReady triggers client onLocalJsReady`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.jsReady
         */
        postMessage("""{"type":"jsReady"}""")
        verify { mockWebViewClient.onLocalJsReady() }
    }

    @Test
    fun `handShook triggers client onJsHandshakeCompleted`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.handShook
         */
        postMessage("""{"type":"handShook"}""")
        verify { mockWebViewClient.onJsHandshakeCompleted() }
    }

    @Test
    fun `formWillAppear triggers show`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.show
         */
        postMessage("""{"type":"formWillAppear"}""")
        verify { mockPresentationManager.present(null) }
    }

    @Test
    fun `formWillAppear triggers show with IDs`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.show
         */
        postMessage("""{"type":"formWillAppear", "data":{"formId":"64CjgW"}}""")
        verify { mockPresentationManager.present("64CjgW") }
    }

    @Test
    fun `trackAggregateEvent enqueues API request`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.createAggregateEvent
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
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.createProfileEvent
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

    private val deeplinkMessage = """
        {
          "type": "openDeepLink",
          "data": {
            "ios": "klaviyotest://settings",
            "android": "klaviyotest://settings"
          }
        }
    """.trimIndent()

    @Test
    fun `openDeepLink broadcasts intent to start activity`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.deepLink
         */
        mockkObject(DeepLinking)
        every { DeepLinking.handleDeepLink(any<Uri>()) } returns Unit
        postMessage(deeplinkMessage)
        verify { DeepLinking.handleDeepLink(mockUri) }
    }

    @Test
    fun `formDisappeared triggers close`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.close
         */
        postMessage("""{"type":"formDisappeared"}""")
        verify { mockPresentationManager.dismiss() }
    }

    @Test
    fun `abort triggers closes`() {
        /**
         * @see com.klaviyo.forms.bridge.KlaviyoNativeBridge.abort
         */
        postMessage("""{"type":"abort"}""")
        verify(exactly = 1) { Klaviyo.unregisterFromInAppForms() }
        postMessage("""{"type":"abort", "reason":"Because the test requires it"}""")
        verify(exactly = 2) { Klaviyo.unregisterFromInAppForms() }
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

    @Test
    fun `unknown type throws an error`() {
        postMessage("""{"type":"unknown"}""")

        verify {
            spyLog.error(
                "Failed to relay webview message: {\"type\":\"unknown\"}",
                any<IllegalStateException>()
            )
        }
    }

    @Test
    fun `null message logs warning`() {
        postMessage(null)

        verify {
            spyLog.warning(
                "Received null message from webview",
                null
            )
        }
    }
}
