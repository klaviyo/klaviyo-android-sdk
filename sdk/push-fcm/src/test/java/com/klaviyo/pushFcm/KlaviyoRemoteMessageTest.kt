package com.klaviyo.pushFcm

import android.content.Intent
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class KlaviyoRemoteMessageTest : BaseTest() {
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
                "label" to "View Order",
                "action" to "deep_link",
                "url" to "klaviyotest://view-order"
            ),
            mapOf(
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
        assert(firstButton?.label == "View Order")
        assert((firstButton as? ActionButton.DeepLink)?.url == "klaviyotest://view-order")

        // Second button is OpenApp type
        val secondButton = buttons?.get(1)
        assert(secondButton is ActionButton.OpenApp)
        assert(secondButton?.label == "Open App")
    }

    @Test
    fun `Test Action Buttons returns null when not present`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        assert(msg.actionButtons == null)
    }

    @Test
    fun `Test parser handles case insensitive action types`() {
        val actionButtonsData = listOf(
            mapOf(
                "label" to "Test 1",
                "action" to "DEEP_LINK",
                "url" to "test://url1"
            ),
            mapOf(
                "label" to "Test 2",
                "action" to "Deep_Link",
                "url" to "test://url2"
            ),
            mapOf(
                "label" to "Test 3",
                "action" to "OPEN_APP"
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
        assert(buttons?.get(1) is ActionButton.DeepLink)
        assert(buttons?.get(2) is ActionButton.OpenApp)
    }

    @Test
    fun `Test parser defaults to OpenApp for unknown action values`() {
        val actionButtonsData = listOf(
            mapOf("label" to "Test 1", "action" to "unknown_action"),
            mapOf("label" to "Test 2", "action" to ""),
            mapOf("label" to "Test 3", "action" to "invalid")
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
        assert(buttons!!.all { it is ActionButton.OpenApp })
    }

    @Test
    fun `Test Action Button with OPEN_APP and no URL`() {
        val actionButtonsData = listOf(
            mapOf(
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
        assert(button?.label == "Open App")
    }

    @Test
    fun `Test Action Button with empty URL creates OpenApp type`() {
        val actionButtonsData = listOf(
            mapOf(
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
    fun `Test DEEP_LINK Action Button without URL is skipped`() {
        val actionButtonsData = listOf(
            mapOf(
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
        assert(buttons == null)
    }

    @Test
    fun `Test parser filters out invalid buttons but keeps valid ones`() {
        val actionButtonsData = listOf(
            mapOf(
                "label" to "",
                "action" to "open_app"
            ),
            mapOf(
                "label" to "Valid Button",
                "action" to "open_app"
            ),
            mapOf(
                "label" to "Invalid - deep link no url",
                "action" to "deep_link"
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
        assert(buttons?.get(0)?.label == "Valid Button")
    }

    @Test
    fun `Test appendActionButtonExtras adds tracking data for OpenApp button`() {
        val intent = mockk<Intent>(relaxed = true)
        val button = ActionButton.OpenApp(
            label = "Open App"
        )

        every { intent.putExtra(any<String>(), any<String>()) } returns intent

        intent.appendActionButtonExtras(button)

        verify { intent.putExtra("com.klaviyo.Button Label", "Open App") }
        verify { intent.putExtra("com.klaviyo.Button Action", "Open App") }
        verify(exactly = 0) { intent.putExtra("com.klaviyo.Button Link", any<String>()) }
    }

    @Test
    fun `Test appendActionButtonExtras adds tracking data for DeepLink button`() {
        val intent = mockk<Intent>(relaxed = true)
        val button = ActionButton.DeepLink(
            label = "View Order",
            url = "klaviyotest://order/123"
        )

        every { intent.putExtra(any<String>(), any<String>()) } returns intent

        intent.appendActionButtonExtras(button)

        verify { intent.putExtra("com.klaviyo.Button Label", "View Order") }
        verify { intent.putExtra("com.klaviyo.Button Action", "Deep Link") }
        verify { intent.putExtra("com.klaviyo.Button Link", "klaviyotest://order/123") }
    }

    @Test
    fun `Test parser enforces maximum of 3 action buttons`() {
        val actionButtonsData = listOf(
            mapOf("label" to "Button 1", "action" to "open_app"),
            mapOf("label" to "Button 2", "action" to "open_app"),
            mapOf("label" to "Button 3", "action" to "open_app"),
            mapOf("label" to "Button 4", "action" to "open_app"),
            mapOf("label" to "Button 5", "action" to "open_app")
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
        assert(buttons?.get(0)?.label == "Button 1")
        assert(buttons?.get(1)?.label == "Button 2")
        assert(buttons?.get(2)?.label == "Button 3")
    }
}
