package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Constants
import com.klaviyo.core.config.AutomaticPushTokenForwarding
import com.klaviyo.core.config.getManifestBoolean
import com.klaviyo.core.config.hasManifestKey
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.BODY_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.KEY_VALUE_PAIRS_KEY
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.TITLE_KEY
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class KlaviyoPushServiceTest : BaseTest() {
    private val stubPushToken = "stub_token"
    private val pushService = spyk(KlaviyoPushService())
    private val stubKeyValuePairs = mapOf(
        "test_key_1" to "test_value_1",
        "test_key_2" to "test_value_2",
        "test_key_3" to "test_value_3"
    )
    private val stubMessage = mutableMapOf(
        "_k" to "",
        TITLE_KEY to "",
        BODY_KEY to "",
        KEY_VALUE_PAIRS_KEY to JSONObject(stubKeyValuePairs).toString()
    )

    @Before
    override fun setup() {
        super.setup()
        // mockkStatic is required for @JvmStatic methods
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        every { Klaviyo.setPushToken(any()) } returns Klaviyo

        mockkConstructor(KlaviyoNotification::class)

        every { anyConstructed<KlaviyoNotification>().displayNotification(any()) } returns true

        // onNewToken reads the forwarding flag from the service Context (not Registry.config), so
        // stub the Context extensions it resolves the three-valued flag through. Default: no key
        // present, i.e. UNSET.
        every { pushService.applicationContext } returns mockContext
        mockkStatic("com.klaviyo.core.config.KlaviyoConfigKt")
        every { mockContext.getManifestBoolean(any(), any()) } answers { thirdArg() }
        every { mockContext.hasManifestKey(any()) } returns false
    }

    private fun setAutomaticPushTokenForwarding(state: AutomaticPushTokenForwarding) {
        every {
            mockContext.hasManifestKey(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING)
        } returns (state != AutomaticPushTokenForwarding.UNSET)
        every {
            mockContext.getManifestBoolean(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING, false)
        } returns (state == AutomaticPushTokenForwarding.ENABLED)
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkStatic(Klaviyo::class)
        unmockkStatic("com.klaviyo.core.config.KlaviyoConfigKt")
    }

    @Test
    fun `FCM onNewToken forwards the new token when the forwarding flag is absent`() {
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.UNSET)

        pushService.onNewToken(stubPushToken)

        verify { Klaviyo.setPushToken(stubPushToken) }
    }

    @Test
    fun `FCM onNewToken forwards the new token when automatic token forwarding is explicitly on`() {
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.ENABLED)

        pushService.onNewToken(stubPushToken)

        verify { Klaviyo.setPushToken(stubPushToken) }
    }

    @Test
    fun `FCM onNewToken does not forward the token when automatic token forwarding is off`() {
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.DISABLED)

        pushService.onNewToken(stubPushToken)

        verify(inverse = true) { Klaviyo.setPushToken(any()) }
    }

    @Test
    fun `A RemoteMessage with notification data is passed on to displayNotification`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify { anyConstructed<KlaviyoNotification>().displayNotification(any()) }
    }

    @Test
    fun `Handling RemoteMessage does NOT enqueue $opened_push API Call`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns mapOf(
            "_k" to "",
            "title" to "",
            "body" to ""
        )

        pushService.onMessageReceived(msg)

        verify(inverse = true) { Klaviyo.handlePush(any()) }
    }

    @Test
    fun `Silent push is not displayed`() {
        val msg = mockk<RemoteMessage>()
        stubMessage.remove("title")
        stubMessage.remove("body")
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify(inverse = true) { anyConstructed<KlaviyoNotification>().displayNotification(any()) }
    }

    @Test
    fun `Custom data handler is called`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify { pushService.onKlaviyoCustomDataMessageReceived(stubKeyValuePairs, msg) }
    }

    @Test
    fun `Non-klaviyo RemoteMessage is ignored`() {
        val msg = mockk<RemoteMessage>()
        stubMessage.remove("_k")
        every { msg.data } returns stubMessage

        pushService.onMessageReceived(msg)

        verify(inverse = true) { anyConstructed<KlaviyoNotification>().displayNotification(any()) }
    }

    @Test
    fun `A Klaviyo notification is dispatched to push observers before the overridable handler`() {
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage
        every { msg.messageId } returns UUID.randomUUID().toString()
        val observer = mockk<KlaviyoNotificationObserver>(relaxed = true)
        KlaviyoPushObservers.onKlaviyoNotification(observer)

        try {
            pushService.onMessageReceived(msg)

            verifyOrder {
                observer(msg)
                pushService.onKlaviyoNotificationMessageReceived(msg)
            }
        } finally {
            KlaviyoPushObservers.offKlaviyoNotification(observer)
        }
    }

    @Test
    fun `The default service path reaches push observers once per message`() {
        // Run the real displayNotification up to its permission check, so both dispatch sites run
        every { anyConstructed<KlaviyoNotification>().displayNotification(any()) } answers { callOriginal() }
        every { anyConstructed<KlaviyoNotification>().hasNotificationPermission(any()) } returns false
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage
        every { msg.messageId } returns UUID.randomUUID().toString()
        val observer = mockk<KlaviyoNotificationObserver>(relaxed = true)
        KlaviyoPushObservers.onKlaviyoNotification(observer)

        try {
            pushService.onMessageReceived(msg)

            verify { anyConstructed<KlaviyoNotification>().hasNotificationPermission(any()) }
            verify(exactly = 1) { observer(msg) }
        } finally {
            KlaviyoPushObservers.offKlaviyoNotification(observer)
        }
    }

    @Test
    fun `A subclass that replaces notification handling still dispatches to push observers`() {
        val customService = spyk(object : KlaviyoPushService() {
            override fun onKlaviyoNotificationMessageReceived(message: RemoteMessage) = Unit
        })
        val msg = mockk<RemoteMessage>()
        every { msg.data } returns stubMessage
        every { msg.messageId } returns UUID.randomUUID().toString()
        val observer = mockk<KlaviyoNotificationObserver>(relaxed = true)
        KlaviyoPushObservers.onKlaviyoNotification(observer)

        try {
            customService.onMessageReceived(msg)

            verify { observer(msg) }
        } finally {
            KlaviyoPushObservers.offKlaviyoNotification(observer)
        }
    }

    @Test
    fun `A silent push is not dispatched to push observers`() {
        val msg = mockk<RemoteMessage>()
        stubMessage.remove("title")
        stubMessage.remove("body")
        every { msg.data } returns stubMessage
        val observer = mockk<KlaviyoNotificationObserver>(relaxed = true)
        KlaviyoPushObservers.onKlaviyoNotification(observer)

        try {
            pushService.onMessageReceived(msg)

            verify(exactly = 0) { observer(any()) }
        } finally {
            KlaviyoPushObservers.offKlaviyoNotification(observer)
        }
    }
}
