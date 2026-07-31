package com.klaviyo.pushFcm

import android.content.Intent
import android.net.Uri
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.ACTION_BUTTONS_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.BODY_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.KEY_VALUE_PAIRS_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.TITLE_KEY
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.ActionButton
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.actionButtons
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.appendActionButtonExtras
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.hasKlaviyoKeyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.keyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.webUrl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KlaviyoRemoteMessageTest : BaseTest() {
    @Before
    override fun setup() {
        super.setup()
        mockkStatic(Uri::class)
    }

    @After
    override fun cleanup() {
        unmockkStatic(Uri::class)
        super.cleanup()
    }

    private val stubKeyValuePairs = mapOf(
        "test_key_1" to "test_value_1",
        "test_key_2" to "test_value_2",
        "test_key_3" to "test_value_3"
    )
    private val stubMessage = mutableMapOf(
        "_k" to "",
        TITLE_KEY to "test title",
        BODY_KEY to "test body",
        KEY_VALUE_PAIRS_KEY to JSONObject(stubKeyValuePairs).toString()
    )

    @Test
    fun `Test isKlaviyoMessage`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.isKlaviyoMessage)
    }

    @Test
    fun `Test isKlaviyoNotification`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.isKlaviyoNotification)
    }

    @Test
    fun `Test message is silent push`() {
        val msg = mockk<RemoteMessage>()

        stubMessage.remove("title")
        stubMessage.remove("body")
        every { msg.data } returns stubMessage

        assert(!msg.isKlaviyoNotification)
    }

    @Test
    fun `Test Key-Value Pairs Deserialization`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.hasKlaviyoKeyValuePairs)
        assert(msg.keyValuePairs == stubKeyValuePairs)
    }

    @Test
    fun `Test Action Buttons Deserialization`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "com.klaviyo.test.view",
                "label" to "View Order",
                "action" to "deep_link",
                "url" to "klaviyotest://view-order"
            ),
            mapOf(
                "id" to "com.klaviyo.test.open",
                "label" to "Open App",
                "action" to "open_app"
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 2)

        // First button is DeepLink type
        val firstButton = buttons?.get(0)
        assert(firstButton is ActionButton.DeepLink)
        assert(firstButton?.id == "com.klaviyo.test.view")
        assert(firstButton?.label == "View Order")
        assert((firstButton as? ActionButton.DeepLink)?.url == "klaviyotest://view-order")

        // Second button is OpenApp type
        val secondButton = buttons?.get(1)
        assert(secondButton is ActionButton.OpenApp)
        assert(secondButton?.id == "com.klaviyo.test.open")
        assert(secondButton?.label == "Open App")
    }

    @Test
    fun `Test Action Buttons returns null when not present`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.actionButtons == null)
    }

    @Test
    fun `Test parser correctly handles exact case-sensitive action types`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "deepLink",
                "label" to "Deep Link Button",
                "action" to "deep_link", // Correct case
                "url" to "test://url"
            ),
            mapOf(
                "id" to "openApp",
                "label" to "Open App Button",
                "action" to "open_app" // Correct case
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 2)
        assert(buttons?.get(0) is ActionButton.DeepLink)
        assert(buttons?.get(1) is ActionButton.OpenApp)
    }

    @Test
    fun `Test parser is case-sensitive and skips incorrect casing`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "test1",
                "label" to "Test 1",
                "action" to "DEEP_LINK", // Wrong case
                "url" to "test://url1"
            ),
            mapOf(
                "id" to "test2",
                "label" to "Test 2",
                "action" to "Deep_Link", // Wrong case
                "url" to "test://url2"
            ),
            mapOf(
                "id" to "test3",
                "label" to "Test 3",
                "action" to "OPEN_APP" // Wrong case
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        // All buttons should be skipped due to incorrect casing
        assert(buttons == null)
    }

    @Test
    fun `Test parser skips buttons with unknown action types`() {
        val actionButtonsData = listOf(
            mapOf("id" to "test1", "label" to "Test 1", "action" to "unknown_action"),
            mapOf("id" to "test2", "label" to "Test 2", "action" to "invalid"),
            mapOf("id" to "test3", "label" to "Test 3", "action" to "future_type")
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        // All buttons should be skipped due to unknown action types
        assert(buttons == null)
    }

    @Test
    fun `Test Action Button with OPEN_APP and no URL`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "com.klaviyo.test.open",
                "label" to "Open App",
                "action" to "open_app"
                // No URL provided
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)

        val button = buttons?.get(0)
        assert(button is ActionButton.OpenApp)
        assert(button?.id == "com.klaviyo.test.open")
        assert(button?.label == "Open App")
    }

    @Test
    fun `Test Action Button with empty URL creates OpenApp type`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "open.app",
                "label" to "Open App",
                "action" to "open_app",
                "url" to ""
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)
        assert(buttons?.get(0) is ActionButton.OpenApp)
    }

    @Test
    fun `Test Action Button with blank URL creates OpenApp type`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "open.app",
                "label" to "Open App",
                "action" to "open_app",
                "url" to "   "
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)
        assert(buttons?.get(0) is ActionButton.OpenApp)
    }

    @Test
    fun `Test Action Button with blank label is skipped`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "open.app",
                "label" to "",
                "action" to "open_app"
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons == null)
    }

    @Test
    fun `Test Action Button with null label is skipped`() {
        val actionButtonsJson = JSONArray().put(
            JSONObject()
                .put("id", "open.app")
                .put("label", JSONObject.NULL)
                .put("action", "open_app")
        ).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons == null)
    }

    @Test
    fun `Test DEEP_LINK Action Button without URL renders as a degraded app-launch button`() {
        // Previously this button was dropped entirely (encoding the render-eligibility-vs-action-
        // validity bug from PUSH-1095). A DEEP_LINK button missing its url still has a valid id,
        // label, and recognized action type, so it must render - degrading to an app-launch button
        // rather than disappearing.
        val actionButtonsData = listOf(
            mapOf(
                "id" to "deep.link",
                "label" to "Deep Link",
                "action" to "deep_link"
                // No URL
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)
        val button = buttons?.get(0)
        assert(button is ActionButton.Degraded)
        assert(button?.id == "deep.link")
        assert(button?.label == "Deep Link")
    }

    @Test
    fun `Test DEEP_LINK Action Button with null URL renders as a degraded app-launch button`() {
        // Same relaxation as above for an explicit JSON null (as opposed to an absent key).
        val actionButtonsJson = JSONArray().put(
            JSONObject()
                .put("id", "deep.link")
                .put("label", "Deep Link")
                .put("action", "deep_link")
                .put("url", JSONObject.NULL)
        ).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)
        val button = buttons?.get(0)
        assert(button is ActionButton.Degraded)
        assert(button?.id == "deep.link")
        assert(button?.label == "Deep Link")
    }

    @Test
    fun `Test parser skips unrecoverable buttons, downgrades URL-invalid ones, and keeps valid ones`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "valid.deep.link",
                "label" to "Valid Deep Link",
                "action" to "deep_link",
                "url" to "test://valid"
            ),
            mapOf(
                // No longer dropped: renders as OpenApp since id/label/action are still valid.
                "id" to "invalid.deep.link",
                "label" to "Invalid - deep link no url",
                "action" to "deep_link"
            ),
            mapOf(
                "id" to "valid.button",
                "label" to "Valid Button",
                "action" to "open_app"
            ),
            mapOf(
                "id" to "invalid.button",
                "label" to "Invalid - wrong case",
                "action" to "OPEN_APP"
            ),
            mapOf(
                "id" to "",
                "label" to "",
                "action" to "open_app"
            )
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 3)
        assert(buttons?.get(0) is ActionButton.DeepLink)
        assert(buttons?.get(0)?.label == "Valid Deep Link")
        assert(buttons?.get(1) is ActionButton.Degraded)
        assert(buttons?.get(1)?.label == "Invalid - deep link no url")
        assert(buttons?.get(2) is ActionButton.OpenApp)
        assert(buttons?.get(2)?.label == "Valid Button")
    }

    @Test
    fun `Test appendActionButtonExtras adds tracking data for OpenApp button`() {
        val intent = mockk<Intent>(relaxed = true)
        val button = ActionButton.OpenApp(
            id = "open.app",
            label = "Open App"
        )

        every { intent.putExtra(any<String>(), any<String>()) } returns intent

        intent.appendActionButtonExtras(button)

        verify { intent.putExtra("com.klaviyo.Button ID", "open.app") }
        verify { intent.putExtra("com.klaviyo.Button Label", "Open App") }
        verify { intent.putExtra("com.klaviyo.Button Action", "Open App") }
        verify(exactly = 0) { intent.putExtra("com.klaviyo.Button Link", any<String>()) }
    }

    @Test
    fun `Test appendActionButtonExtras adds tracking data for DeepLink button`() {
        val intent = mockk<Intent>(relaxed = true)
        val button = ActionButton.DeepLink(
            id = "view.order",
            label = "View Order",
            url = "klaviyotest://order/123"
        )

        every { intent.putExtra(any<String>(), any<String>()) } returns intent

        intent.appendActionButtonExtras(button)

        verify { intent.putExtra("com.klaviyo.Button ID", "view.order") }
        verify { intent.putExtra("com.klaviyo.Button Label", "View Order") }
        verify { intent.putExtra("com.klaviyo.Button Action", "Deep Link") }
        verify { intent.putExtra("com.klaviyo.Button Link", "klaviyotest://order/123") }
    }

    @Test
    fun `Test parser enforces maximum of 3 valid action buttons`() {
        val actionButtonsData = listOf(
            mapOf("id" to "button1", "label" to "Button 1", "action" to "open_app"),
            mapOf("id" to "button2", "label" to "Button 2", "action" to "open_app"),
            mapOf("id" to "button3", "label" to "Button 3", "action" to "open_app"),
            mapOf("id" to "button4", "label" to "Button 4", "action" to "open_app"),
            mapOf("id" to "button5", "label" to "Button 5", "action" to "open_app")
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 3)
        assert(buttons?.get(0)?.id == "button1")
        assert(buttons?.get(0)?.label == "Button 1")
        assert(buttons?.get(1)?.id == "button2")
        assert(buttons?.get(1)?.label == "Button 2")
        assert(buttons?.get(2)?.id == "button3")
        assert(buttons?.get(2)?.label == "Button 3")
    }

    @Test
    fun `Test parser continues past invalid buttons to reach 3 valid buttons`() {
        val actionButtonsData = listOf(
            mapOf("id" to "", "label" to "", "action" to "open_app"), // Invalid - no id/label
            mapOf("id" to "valid1", "label" to "Valid 1", "action" to "open_app"), // Valid
            mapOf("id" to "invalid1", "label" to "Invalid", "action" to "OPEN_APP"), // Invalid - wrong case
            mapOf(
                "id" to "valid2",
                "label" to "Valid 2",
                "action" to "deep_link",
                "url" to "test://2"
            ), // Valid
            // Invalid - unsupported action type (a deep_link with no url no longer counts as
            // invalid post-PUSH-1095: it downgrades to a valid, rendered OpenApp button instead).
            mapOf("id" to "invalid2", "label" to "Invalid", "action" to "unsupported_action"),
            mapOf("id" to "valid3", "label" to "Valid 3", "action" to "open_app"), // Valid
            mapOf("id" to "valid4", "label" to "Valid 4", "action" to "open_app") // Should be ignored (>3 valid)
        )
        val actionButtonsJson = JSONArray(actionButtonsData).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 3)
        assert(buttons?.get(0)?.id == "valid1")
        assert(buttons?.get(0)?.label == "Valid 1")
        assert(buttons?.get(1)?.id == "valid2")
        assert(buttons?.get(1)?.label == "Valid 2")
        assert(buttons?.get(2)?.id == "valid3")
        assert(buttons?.get(2)?.label == "Valid 3")
    }

    @Test
    fun `webUrl returns Uri when web_url is an https URL`() {
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns "https"
        every { Uri.parse("https://example.com") } returns mockUri

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage.toMutableMap().apply {
            put(KlaviyoNotification.WEB_URL_KEY, "https://example.com")
        }

        val webUrl = msg.webUrl
        assert(webUrl != null)
        verify { Uri.parse("https://example.com") }
    }

    @Test
    fun `webUrl returns Uri when web_url is an http URL`() {
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns "http"
        every { Uri.parse("http://example.com") } returns mockUri

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage.toMutableMap().apply {
            put(KlaviyoNotification.WEB_URL_KEY, "http://example.com")
        }

        assert(msg.webUrl != null)
    }

    @Test
    fun `webUrl returns null when web_url is missing`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.webUrl == null)
    }

    @Test
    fun `webUrl returns null when web_url is blank`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage.toMutableMap().apply {
            put(KlaviyoNotification.WEB_URL_KEY, "")
        }

        assert(msg.webUrl == null)
    }

    @Test
    fun `webUrl returns null when web_url has a blocked scheme`() {
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns "javascript"
        every { Uri.parse("javascript:alert(1)") } returns mockUri

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage.toMutableMap().apply {
            put(KlaviyoNotification.WEB_URL_KEY, "javascript:alert(1)")
        }

        assert(msg.webUrl == null)
    }

    @Test
    fun `webUrl returns url when web_url has an allowlisted communication scheme`() {
        val schemes = listOf(
            "mailto" to "mailto:user@example.com",
            "tel" to "tel:+15555550100",
            "sms" to "sms:+15555550100",
            "smsto" to "smsto:+15555550100"
        )

        for ((scheme, url) in schemes) {
            val mockUri = mockk<Uri>(relaxed = true)
            every { mockUri.scheme } returns scheme
            every { Uri.parse(url) } returns mockUri

            val msg = mockk<RemoteMessage>()
            every { msg.data } returns stubMessage.toMutableMap().apply {
                put(KlaviyoNotification.WEB_URL_KEY, url)
            }

            assert(msg.webUrl == url) {
                "Expected webUrl to return '$url' for scheme $scheme"
            }
        }
    }

    @Test
    fun `webUrl returns parsed URL even when url field is also present`() {
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns "https"
        every { Uri.parse("https://example.com") } returns mockUri

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage.toMutableMap().apply {
            put(KlaviyoNotification.WEB_URL_KEY, "https://example.com")
            put(KlaviyoNotification.URL_KEY, "myapp://home")
        }

        assert(msg.webUrl != null)
    }

    @Test
    fun `actionButtons parses open_url variant with url`() {
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns "https"
        every { Uri.parse("https://example.com") } returns mockUri

        val actionButtonsData = listOf(
            mapOf(
                "id" to "open.url",
                "label" to "Open Website",
                "action" to "open_url",
                "url" to "https://example.com"
            )
        )
        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, JSONArray(actionButtonsData).toString())
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)

        val button = buttons?.get(0)
        assert(button is ActionButton.OpenUrl)
        assert(button?.id == "open.url")
        assert(button?.label == "Open Website")
        assert((button as? ActionButton.OpenUrl)?.url == "https://example.com")
    }

    @Test
    fun `actionButtons downgrades open_url with blocked scheme to a degraded app-launch button`() {
        // Previously this dropped the button entirely (PUSH-1095): a dispatch-time security
        // allowlist was being misused as a render-time eligibility gate. The button must still
        // render - id/label/action are all valid - but the disallowed-scheme URL degrades the
        // action so a tap falls back to launching the app rather than ever reaching the browser.
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns "intent"
        every { Uri.parse("intent://evil") } returns mockUri

        val actionButtonsData = listOf(
            mapOf(
                "id" to "open.url",
                "label" to "Open Website",
                "action" to "open_url",
                "url" to "intent://evil"
            )
        )
        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, JSONArray(actionButtonsData).toString())
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)
        val button = buttons?.get(0)
        // Downgraded to OpenApp: it carries no url at all, so it is structurally impossible for
        // the disallowed-scheme URL to ever reach the dispatch path (KlaviyoTrampolineActivity).
        assert(button is ActionButton.Degraded)
        assert(button?.id == "open.url")
        assert(button?.label == "Open Website")
    }

    @Test
    fun `actionButtons downgrades open_url with scheme-less url to a degraded app-launch button`() {
        // Regression coverage for the exact PUSH-1095 repro: a scheme-less link like
        // "www.cnn.com" parses to a null Uri.scheme, which is not in ALLOWED_OPEN_URL_SCHEMES.
        // The button must still render.
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.scheme } returns null
        every { Uri.parse("www.cnn.com") } returns mockUri

        val actionButtonsData = listOf(
            mapOf(
                "id" to "open.url",
                "label" to "Open Website",
                "action" to "open_url",
                "url" to "www.cnn.com"
            )
        )
        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, JSONArray(actionButtonsData).toString())
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)
        val button = buttons?.get(0)
        assert(button is ActionButton.Degraded)
        assert(button?.id == "open.url")
        assert(button?.label == "Open Website")
        // The declared action and destination survive the downgrade so the $opened_push
        // metadata matches iOS, which reports "Open URL" plus the raw link for this payload.
        assertEquals(
            ActionButton.DISPLAY_NAME_OPEN_URL,
            (button as ActionButton.Degraded).declaredAction
        )
        assertEquals("www.cnn.com", button.declaredUrl)
    }

    @Test
    fun `actionButtons accepts open_url with allowlisted communication schemes`() {
        val schemes = listOf(
            "mailto" to "mailto:user@example.com",
            "tel" to "tel:+15555550100",
            "sms" to "sms:+15555550100",
            "smsto" to "smsto:+15555550100"
        )

        for ((scheme, url) in schemes) {
            val mockUri = mockk<Uri>(relaxed = true)
            every { mockUri.scheme } returns scheme
            every { Uri.parse(url) } returns mockUri

            val actionButtonsData = listOf(
                mapOf(
                    "id" to "comms.button",
                    "label" to "Contact",
                    "action" to "open_url",
                    "url" to url
                )
            )
            val messageWithActions = stubMessage.toMutableMap().apply {
                put(ACTION_BUTTONS_KEY, JSONArray(actionButtonsData).toString())
            }

            val msg = mockk<RemoteMessage>()
            every { msg.data } returns messageWithActions

            val buttons = msg.actionButtons
            assert(buttons != null) { "Expected button for scheme $scheme to be accepted" }
            assert(buttons?.size == 1) { "Expected 1 button for scheme $scheme" }
            assert(buttons?.get(0) is ActionButton.OpenUrl) {
                "Expected OpenUrl button for scheme $scheme"
            }
            assert((buttons?.get(0) as? ActionButton.OpenUrl)?.url == url) {
                "Expected url $url for scheme $scheme"
            }
        }
    }

    @Test
    fun `actionButtons downgrades open_url with missing url to a degraded app-launch button`() {
        val actionButtonsData = listOf(
            mapOf(
                "id" to "open.url",
                "label" to "Open Website",
                "action" to "open_url"
            )
        )
        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, JSONArray(actionButtonsData).toString())
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)
        val button = buttons?.get(0)
        assert(button is ActionButton.Degraded)
        assert(button?.id == "open.url")
        assert(button?.label == "Open Website")
    }

    @Test
    fun `actionButtons downgrades open_url with blank url to a degraded app-launch button`() {
        // optNonBlankString maps "" and null to the same result today; assert the blank case
        // explicitly so a change to that helper cannot silently drop the button.
        val actionButtonsData = listOf(
            mapOf(
                "id" to "open.url",
                "label" to "Open Website",
                "action" to "open_url",
                "url" to "   "
            )
        )
        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, JSONArray(actionButtonsData).toString())
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons?.size == 1)
        val button = buttons?.get(0)
        assert(button is ActionButton.Degraded)
        assertEquals(
            ActionButton.DISPLAY_NAME_OPEN_URL,
            (button as ActionButton.Degraded).declaredAction
        )
        assertEquals(null, button.declaredUrl)
    }

    @Test
    fun `actionButtons downgrades open_url with null url to a degraded app-launch button`() {
        val actionButtonsJson = JSONArray().put(
            JSONObject()
                .put("id", "open.url")
                .put("label", "Open Website")
                .put("action", "open_url")
                .put("url", JSONObject.NULL)
        ).toString()

        val messageWithActions = stubMessage.toMutableMap().apply {
            put(ACTION_BUTTONS_KEY, actionButtonsJson)
        }

        val msg = mockk<RemoteMessage>()
        every { msg.data } returns messageWithActions

        val buttons = msg.actionButtons
        assert(buttons != null)
        assert(buttons?.size == 1)
        val button = buttons?.get(0)
        assert(button is ActionButton.Degraded)
        assert(button?.id == "open.url")
        assert(button?.label == "Open Website")
    }

    @Test
    fun `appendActionButtonExtras for OpenUrl includes link and display name`() {
        val intent = mockk<Intent>(relaxed = true)
        val button = ActionButton.OpenUrl(
            id = "open.url",
            label = "Open Website",
            url = "https://example.com"
        )

        every { intent.putExtra(any<String>(), any<String>()) } returns intent

        intent.appendActionButtonExtras(button)

        verify { intent.putExtra("com.klaviyo.Button ID", "open.url") }
        verify { intent.putExtra("com.klaviyo.Button Label", "Open Website") }
        verify { intent.putExtra("com.klaviyo.Button Action", "Open URL") }
        verify { intent.putExtra("com.klaviyo.Button Link", "https://example.com") }
    }

    @Test
    fun `appendActionButtonExtras for Degraded reports the declared action and link`() {
        // A downgraded button launches the app, but $opened_push must still report what the
        // sender configured — otherwise the misconfiguration is invisible in analytics and
        // Android disagrees with iOS about the same message.
        val intent = mockk<Intent>(relaxed = true)
        val button = ActionButton.Degraded(
            id = "open.url",
            label = "Open Website",
            declaredAction = ActionButton.DISPLAY_NAME_OPEN_URL,
            declaredUrl = "www.cnn.com"
        )

        every { intent.putExtra(any<String>(), any<String>()) } returns intent

        intent.appendActionButtonExtras(button)

        verify { intent.putExtra("com.klaviyo.Button ID", "open.url") }
        verify { intent.putExtra("com.klaviyo.Button Label", "Open Website") }
        verify { intent.putExtra("com.klaviyo.Button Action", "Open URL") }
        verify { intent.putExtra("com.klaviyo.Button Link", "www.cnn.com") }
    }

    @Test
    fun `appendActionButtonExtras for Degraded omits the link when the payload had none`() {
        val intent = mockk<Intent>(relaxed = true)
        val button = ActionButton.Degraded(
            id = "deep.link",
            label = "Shop Now",
            declaredAction = ActionButton.DISPLAY_NAME_DEEP_LINK,
            declaredUrl = null
        )

        every { intent.putExtra(any<String>(), any<String>()) } returns intent

        intent.appendActionButtonExtras(button)

        verify { intent.putExtra("com.klaviyo.Button Action", "Deep Link") }
        verify(exactly = 0) { intent.putExtra("com.klaviyo.Button Link", any<String>()) }
    }
}
