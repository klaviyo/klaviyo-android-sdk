package com.klaviyo.analytics

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.klaviyo.analytics.linking.DeepLinkHandler
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateSideEffects
import com.klaviyo.core.Constants
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.MissingAPIKey
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.Queue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class KlaviyoPushOpenHandlerTest : BaseTest() {

    companion object {
        val stubIntentExtras = mapOf(
            "com.klaviyo.body" to "Message body",
            "com.klaviyo._k" to """{
              "Push Platform": "android",
              "$\flow": "",
              "$\message": "01GK4P5W6AV4V3APTJ727JKSKQ",
              "$\variation": "",
              "Message Name": "check_push_pipeline",
              "Message Type": "campaign",
              "c": "6U7nPA",
              "cr": "31698553996657051350694345805149781",
              "m": "01GK4P5W6AV4V3APTJ727JKSKQ",
              "t": "1671205224",
              "timestamp": "2022-12-16T15:40:24.049427+00:00",
              "x": "manual"
            }"""
        )

        fun mockIntent(payload: Map<String, String>, uri: Uri? = null) =
            MockIntent.mockIntentWith(payload, uri).intent
    }

    private val mockApiClient: ApiClient = mockk<ApiClient>().apply {
        every { startService() } returns Unit
        every { onApiRequest(any(), any()) } returns Unit
        every { offApiRequest(any()) } returns Unit
        every { enqueueProfile(any()) } returns mockk(relaxed = true)
        every { enqueueEvent(any(), any()) } returns mockk(relaxed = true)
        every { enqueuePushToken(any(), any()) } returns mockk(relaxed = true)
    }

    private val mockBuilder = mockk<Config.Builder>().apply {
        every { apiKey(any()) } returns this
        every { applicationContext(any()) } returns this
        every { build() } returns mockConfig
    }

    private val mockApplication = mockk<Application>().apply {
        every { mockContext.applicationContext } returns this
        every { unregisterActivityLifecycleCallbacks(any()) } returns Unit
        every { unregisterComponentCallbacks(any()) } returns Unit
        every { registerActivityLifecycleCallbacks(any()) } returns Unit
        every { registerComponentCallbacks(any()) } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()

        every { Registry.configBuilder } returns mockBuilder
        Registry.register<ApiClient>(mockApiClient)
        mockDeviceProperties()
        mockkConstructor(StateSideEffects::class)
        mockkStatic(Uri::class)
        mockkObject(DeepLinking)

        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = mockContext
        )
    }

    @After
    override fun cleanup() {
        unmockkAll()
        drainPreInitQueue()
        Registry.unregister<DeepLinkHandler>()
        Registry.unregister<Config>()
        Registry.unregister<State>()
        Registry.unregister<StateSideEffects>()
        Registry.unregister<ApiClient>()
        super.cleanup()
        unmockDeviceProperties()
    }

    /**
     * Empty [Klaviyo]'s private pre-init queue between tests. A test that makes `enqueueEvent` throw
     * a [com.klaviyo.core.config.KlaviyoException] leaves the operation queued for replay, and since
     * [Klaviyo] is an object that queue outlives `unmockkAll`. The next `initialize` would drain it
     * and enqueue that stale event against the following test's mock.
     */
    private fun drainPreInitQueue() = Klaviyo::class.java
        .getDeclaredField("preInitQueue")
        .also { it.isAccessible = true }
        .let { (it.get(Klaviyo) as Queue<*>).clear() }

    private fun verifyOpenedPushEventEnqueued() = verify(exactly = 1) {
        mockApiClient.enqueueEvent(
            match { event -> event.metric == EventMetric.OPENED_PUSH },
            any()
        )
    }

    private fun captureOpenedPushEvent() = slot<Event>().also {
        every { mockApiClient.enqueueEvent(capture(it), any()) } returns mockk(relaxed = true)
    }

    private fun setupDeepLinkHandler(): Pair<() -> Uri?, DeepLinkHandler> {
        var capturedUri: Uri? = null
        Klaviyo.registerDeepLinkHandler { uri: Uri -> capturedUri = uri }
        return { capturedUri } to Registry.get<DeepLinkHandler>()
    }

    @Test
    fun `Non-klaviyo or null intents are ignored`() {
        // doesn't have _k, klaviyo tracking params
        Klaviyo.handlePush(mockIntent(mapOf("com.other.package.message" to "3rd party push")))
        Klaviyo.handlePush(null)

        verify(inverse = true) { mockApiClient.enqueueEvent(any(), any()) }
    }

    @Test
    fun `handlePush enqueues opened_push event for klaviyo push intent`() {
        Klaviyo.handlePush(mockIntent(stubIntentExtras))
        verifyOpenedPushEventEnqueued()
    }

    @Test
    fun `handlePush includes klaviyo extras and push token in opened_push event`() {
        val eventSlot = captureOpenedPushEvent()
        Registry.get<State>().pushToken = PUSH_TOKEN

        Klaviyo.handlePush(mockIntent(stubIntentExtras))

        assertTrue(eventSlot.isCaptured)
        val capturedEvent = eventSlot.captured
        assertEquals(EventMetric.OPENED_PUSH, capturedEvent.metric)

        // Verify that klaviyo extras are included (with com.klaviyo. prefix removed)
        assertNotNull(capturedEvent[EventKey.CUSTOM("body")])
        assertNotNull(capturedEvent[EventKey.CUSTOM("_k")])

        // Verify push token was in the event
        assertEquals(PUSH_TOKEN, eventSlot.captured[EventKey.PUSH_TOKEN])
    }

    @Test
    fun `handlePush invokes DeepLinkHandler when registered and intent has URI data`() {
        val (getCapturedUri) = setupDeepLinkHandler()
        val testUri = mockk<Uri>()

        Klaviyo.handlePush(mockIntent(stubIntentExtras, testUri))

        assertEquals(testUri, getCapturedUri())
    }

    @Test
    fun `handlePush does not invoke DeepLinkHandler when not registered`() {
        val testUri = mockk<Uri>()

        Klaviyo.handlePush(mockIntent(stubIntentExtras, testUri))

        verifyOpenedPushEventEnqueued()
        verify(inverse = true) { DeepLinking.handleDeepLink(testUri) }
    }

    @Test
    fun `handlePush does not invoke DeepLinkHandler when intent has no URI data`() {
        val (getCapturedUri) = setupDeepLinkHandler()

        Klaviyo.handlePush(null)
        Klaviyo.handlePush(mockIntent(stubIntentExtras))

        assertEquals(null, getCapturedUri())
        verifyOpenedPushEventEnqueued()
    }

    @Test
    fun `handlePush does not invoke deep link handler for non-klaviyo push intents`() {
        val (getCapturedUri) = setupDeepLinkHandler()
        val testUri = mockk<Uri>()

        Klaviyo.handlePush(mockIntent(mapOf("some.other.extra" to "value"), testUri))

        assertEquals(null, getCapturedUri())
        verify(inverse = true) { mockApiClient.enqueueEvent(any(), any()) }
    }

    @Test
    fun `handlePush dismisses the notification when a notification tag is present`() {
        val mockNotificationManager = mockk<NotificationManagerCompat>(relaxed = true)
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns mockNotificationManager
        val notificationTag = "notification-tag-123"

        Klaviyo.handlePush(
            mockIntent(
                mapOf(
                    "com.klaviyo._k" to """{"m":"01GK4P5W6AV4V3APTJ727JKSKQ","tm":"dismissal-tag"}""",
                    Constants.NOTIFICATION_TAG_EXTRA to notificationTag
                )
            )
        )

        verify { mockNotificationManager.cancel(notificationTag, Constants.NOTIFICATION_ID) }
    }

    // --- Push-open dedup guard (keyed on `_k.tm`, else the generated NOTIFICATION_UID_EXTRA) ---
    // Note: Klaviyo is an object, so its in-memory dedup set persists across tests in this class.
    // Each dedup test uses a distinct delivery ID to stay independent of execution order.

    private fun deliveryExtras(deliveryId: String) = mapOf(
        "com.klaviyo.body" to "Message body",
        "com.klaviyo._k" to """{"m":"01GK4P5W6AV4V3APTJ727JKSKQ","tm":"$deliveryId"}"""
    )

    private fun deliveryIntent(deliveryId: String, uri: Uri? = null): Intent =
        mockIntent(deliveryExtras(deliveryId), uri)

    /**
     * A delivery intent flagged with [Constants.SUPPRESS_DEEP_LINK_HANDLER_EXTRA], as the trampoline
     * stamps its own intents before forwarding an unflagged copy to the host.
     */
    private fun suppressedIntent(deliveryId: String, uri: Uri? = null): Intent =
        deliveryIntent(deliveryId, uri).apply {
            every { getBooleanExtra(Constants.SUPPRESS_DEEP_LINK_HANDLER_EXTRA, false) } returns true
        }

    /** A Klaviyo intent whose `_k` payload omits `tm`, relying on the generated uid as the key. */
    private fun tmLessIntent(notificationUid: String): Intent =
        mockIntent(
            mapOf(
                "com.klaviyo._k" to """{"m":"01GK4P5W6AV4V3APTJ727JKSKQ"}""",
                Constants.NOTIFICATION_UID_EXTRA to notificationUid
            )
        )

    @Test
    fun `handlePush tracks and dispatches once when a delivery is handled twice`() {
        // A host calling handlePush twice for one tap gets one event and one navigation.
        val (getCapturedUri) = setupDeepLinkHandler()
        val testUri = mockk<Uri>()

        Klaviyo.handlePush(deliveryIntent("dedup-twice-distinct-intents", testUri))
        Klaviyo.handlePush(deliveryIntent("dedup-twice-distinct-intents", testUri))

        verifyOpenedPushEventEnqueued()
        assertEquals(testUri, getCapturedUri())
        verify(exactly = 1) { DeepLinking.handleDeepLink(testUri) }
    }

    @Test
    fun `handlePush dispatches once when a suppressed intent precedes two unflagged calls`() {
        // The suppressed call must not consume the dispatch, and the two that follow must
        // collapse to one navigation.
        val (getCapturedUri) = setupDeepLinkHandler()
        val testUri = mockk<Uri>()

        Klaviyo.handlePush(suppressedIntent("suppressed-then-two", testUri))
        Klaviyo.handlePush(deliveryIntent("suppressed-then-two", testUri))
        Klaviyo.handlePush(deliveryIntent("suppressed-then-two", testUri))

        verifyOpenedPushEventEnqueued()
        assertEquals(testUri, getCapturedUri())
        verify(exactly = 1) { DeepLinking.handleDeepLink(testUri) }
    }

    @Test
    fun `handlePush dispatches a deep link with no delivery id every time`() {
        // No tm and no uid → nothing is deduped, matching the tracking stage's behavior.
        val (getCapturedUri) = setupDeepLinkHandler()
        val testUri = mockk<Uri>()
        val noKey = mapOf("com.klaviyo.body" to "Message body", "com.klaviyo._k" to "{}")

        Klaviyo.handlePush(mockIntent(noKey, testUri))
        Klaviyo.handlePush(mockIntent(noKey, testUri))

        assertEquals(testUri, getCapturedUri())
        verify(exactly = 2) { DeepLinking.handleDeepLink(testUri) }
    }

    @Test
    fun `handlePush on a suppressed intent tracks and dismisses without invoking the handler`() {
        val (getCapturedUri) = setupDeepLinkHandler()
        val testUri = mockk<Uri>()

        Klaviyo.handlePush(suppressedIntent("suppressed-dispatch", testUri))

        verifyOpenedPushEventEnqueued()
        assertEquals(null, getCapturedUri())
        verify(exactly = 0) { DeepLinking.handleDeepLink(any()) }
    }

    @Test
    fun `handlePush after a suppressed intent invokes the handler without tracking again`() {
        // The trampoline handles its flagged intent, then forwards an unflagged copy; a host that
        // still calls handlePush must reach their handler, without a second $opened_push.
        val (getCapturedUri) = setupDeepLinkHandler()
        val testUri = mockk<Uri>()

        Klaviyo.handlePush(suppressedIntent("suppressed-then-manual", testUri))
        Klaviyo.handlePush(deliveryIntent("suppressed-then-manual", testUri))

        verifyOpenedPushEventEnqueued()
        assertEquals(testUri, getCapturedUri())
        verify(exactly = 1) { DeepLinking.handleDeepLink(testUri) }
    }

    @Test
    fun `handlePush dismisses a notification only once across repeat deliveries`() {
        val tag = "dedup-dismiss-tag"
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notificationManager
        val intent = mockIntent(
            mapOf(
                "com.klaviyo._k" to """{"m":"01GK4P5W6AV4V3APTJ727JKSKQ","tm":"dedup-dismiss"}""",
                Constants.NOTIFICATION_TAG_EXTRA to tag
            )
        )

        try {
            Klaviyo.handlePush(intent)
            Klaviyo.handlePush(intent)

            verify(exactly = 1) { notificationManager.cancel(tag, Constants.NOTIFICATION_ID) }
        } finally {
            unmockkStatic(NotificationManagerCompat::class)
        }
    }

    @Test
    fun `handlePush short-circuits a repeat call with the same intent`() {
        val intent = deliveryIntent("dedup-same-intent-object")

        Klaviyo.handlePush(intent)
        Klaviyo.handlePush(intent)

        verifyOpenedPushEventEnqueued()
    }

    @Test
    fun `handlePush tracks distinct deliveries independently`() {
        Klaviyo.handlePush(deliveryIntent("dedup-distinct-A"))
        Klaviyo.handlePush(deliveryIntent("dedup-distinct-B"))

        verify(exactly = 2) {
            mockApiClient.enqueueEvent(match { it.metric == EventMetric.OPENED_PUSH }, any())
        }
    }

    @Test
    fun `handlePush dedupes a tm-less delivery via the generated notification uid`() {
        Klaviyo.handlePush(tmLessIntent("uid-fallback-same"))
        Klaviyo.handlePush(tmLessIntent("uid-fallback-same"))

        verifyOpenedPushEventEnqueued()
    }

    @Test
    fun `handlePush tracks distinct tm-less notifications independently`() {
        // Distinct notifications get distinct generated uids, so they must not collide.
        Klaviyo.handlePush(tmLessIntent("uid-fallback-A"))
        Klaviyo.handlePush(tmLessIntent("uid-fallback-B"))

        verify(exactly = 2) {
            mockApiClient.enqueueEvent(match { it.metric == EventMetric.OPENED_PUSH }, any())
        }
    }

    @Test
    fun `handlePush falls back to the generated uid when the _k payload is not valid JSON`() {
        // A non-JSON _k must fail tm parsing gracefully and fall back to the generated uid.
        val makeIntent = {
            mockIntent(
                mapOf(
                    "com.klaviyo._k" to "not-json",
                    Constants.NOTIFICATION_UID_EXTRA to "uid-malformed-k"
                )
            )
        }

        Klaviyo.handlePush(makeIntent())
        Klaviyo.handlePush(makeIntent())

        verifyOpenedPushEventEnqueued()
    }

    @Test
    fun `handlePush does not dedupe when no delivery id is available`() {
        // No `tm` and no generated uid: with no key to match on, each call tracks. We never fabricate
        // a key from the shared `_k` metadata, which would collapse distinct deliveries.
        val noKey = mapOf("com.klaviyo._k" to """{"m":"01GK4P5W6AV4V3APTJ727JKSKQ"}""")

        Klaviyo.handlePush(mockIntent(noKey))
        Klaviyo.handlePush(mockIntent(noKey))

        verify(exactly = 2) {
            mockApiClient.enqueueEvent(match { it.metric == EventMetric.OPENED_PUSH }, any())
        }
    }

    @Test
    fun `handlePush continues deep link handling even if opened_push processing fails`() {
        val (getCapturedUri) = setupDeepLinkHandler()
        every { mockApiClient.enqueueEvent(any(), any()) } throws MissingAPIKey()
        val testUri = mockk<Uri>()

        Klaviyo.handlePush(mockIntent(stubIntentExtras, testUri))

        // Deep link handler should still be invoked despite the API error
        assertEquals(testUri, getCapturedUri())
    }

    @Test
    fun `handlePush decodes valid key_value_pairs JSON into a map`() {
        val eventSlot = captureOpenedPushEvent()
        val keyValuePairsJson = """{"custom_key_1":"value1","custom_key_2":"value2"}"""
        val extrasWithKeyValuePairs = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.key_value_pairs" to keyValuePairsJson
        )

        Klaviyo.handlePush(mockIntent(extrasWithKeyValuePairs))

        assertTrue(eventSlot.isCaptured)
        val capturedEvent = eventSlot.captured
        val keyValuePairs = capturedEvent[EventKey.CUSTOM("key_value_pairs")]

        // Verify that the value is a map, not a string
        assertTrue(keyValuePairs is Map<*, *>)
        val map = keyValuePairs as Map<*, *>
        assertEquals("value1", map["custom_key_1"])
        assertEquals("value2", map["custom_key_2"])
        assertEquals(2, map.size)
    }

    @Test
    fun `handlePush falls back to raw string when key_value_pairs JSON is invalid`() {
        val eventSlot = captureOpenedPushEvent()
        val invalidJson = """{"invalid": "json"""
        val extrasWithInvalidKeyValuePairs = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.key_value_pairs" to invalidJson
        )

        Klaviyo.handlePush(mockIntent(extrasWithInvalidKeyValuePairs))

        assertTrue(eventSlot.isCaptured)
        val capturedEvent = eventSlot.captured
        val keyValuePairs = capturedEvent[EventKey.CUSTOM("key_value_pairs")]

        // Verify that the value falls back to the raw string
        assertTrue(keyValuePairs is String)
        assertEquals(invalidJson, keyValuePairs)

        // Verify warning was logged
        verify { spyLog.warning(any(), any()) }
    }

    @Test
    fun `handlePush decodes empty key_value_pairs JSON into empty map`() {
        val eventSlot = captureOpenedPushEvent()
        val emptyJson = """{}"""
        val extrasWithEmptyKeyValuePairs = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.key_value_pairs" to emptyJson
        )

        Klaviyo.handlePush(mockIntent(extrasWithEmptyKeyValuePairs))

        assertTrue(eventSlot.isCaptured)
        val capturedEvent = eventSlot.captured
        val keyValuePairs = capturedEvent[EventKey.CUSTOM("key_value_pairs")]

        // Verify that the value is an empty map
        assertTrue(keyValuePairs is Map<*, *>)
        val map = keyValuePairs as Map<*, *>
        assertTrue(map.isEmpty())
    }

    @Test
    fun `handlePush still decodes other klaviyo extras as strings`() {
        val eventSlot = captureOpenedPushEvent()
        val extrasWithMultipleFields = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.body" to "Test message",
            "com.klaviyo.title" to "Test title"
        )

        Klaviyo.handlePush(mockIntent(extrasWithMultipleFields))

        assertTrue(eventSlot.isCaptured)
        val capturedEvent = eventSlot.captured

        // Verify other fields are still strings
        val body = capturedEvent[EventKey.CUSTOM("body")]
        val title = capturedEvent[EventKey.CUSTOM("title")]

        assertTrue(body is String)
        assertTrue(title is String)
        assertEquals("Test message", body)
        assertEquals("Test title", title)
    }
}
