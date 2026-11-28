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
import com.klaviyo.core.KlaviyoException
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.MissingAPIKey
import com.klaviyo.core.forms.FormsPreviewTokenSink
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
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
     * a [KlaviyoException] leaves the operation queued for replay, and since [Klaviyo] is an object
     * that queue outlives `unmockkAll`. The next `initialize` would drain it and enqueue that stale
     * event against the following test's mock.
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
        val tag = "suppressed-dispatch-tag"
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notificationManager
        val intent = suppressedIntent("suppressed-dispatch", testUri).apply {
            every { getStringExtra(Constants.NOTIFICATION_TAG_EXTRA) } returns tag
        }

        try {
            Klaviyo.handlePush(intent)

            // Suppression scopes to the handler only; the open and dismissal still happen.
            verifyOpenedPushEventEnqueued()
            verify(exactly = 1) { notificationManager.cancel(tag, Constants.NOTIFICATION_ID) }
            assertEquals(null, getCapturedUri())
            verify(exactly = 0) { DeepLinking.handleDeepLink(any()) }
        } finally {
            unmockkStatic(NotificationManagerCompat::class)
        }
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
    fun `handlePush omits key_value_pairs from the event when the JSON is invalid`() {
        val eventSlot = captureOpenedPushEvent()
        val extrasWithInvalidKeyValuePairs = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.key_value_pairs" to """{"invalid": "json"""
        )

        Klaviyo.handlePush(mockIntent(extrasWithInvalidKeyValuePairs))

        assertTrue(eventSlot.isCaptured)
        assertFalse(eventSlot.captured.toMap().containsKey("key_value_pairs"))
        verify { spyLog.warning("Failed to parse key_value_pairs JSON", null) }
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

    // --- getKlaviyoDeepLink: reads the link whether or not an intent-filter matched ---

    @Test
    fun `getKlaviyoDeepLink returns the intent data when an activity resolved the link`() {
        val testUri = mockk<Uri>()

        val link = Klaviyo.getKlaviyoDeepLink(mockIntent(stubIntentExtras, testUri))

        assertEquals(testUri, link)
    }

    @Test
    fun `getKlaviyoDeepLink falls back to the payload url when the intent has no data`() {
        // The unresolvable case: the trampoline sends a launcher intent, which carries no data.
        val parsed = mockk<Uri>()
        every { Uri.parse("klaviyotest://detail/1") } returns parsed
        val extras = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.url" to "klaviyotest://detail/1"
        )

        val link = Klaviyo.getKlaviyoDeepLink(mockIntent(extras))

        assertEquals(parsed, link)
    }

    @Test
    fun `getKlaviyoDeepLink prefers the tapped button's link over the notification body's`() {
        // An action button intent carries both: its own destination and, via appendKlaviyoExtras,
        // the body's url. Returning the body's would navigate somewhere the user did not tap.
        val buttonUri = mockk<Uri>()
        every { Uri.parse("klaviyotest://order/123") } returns buttonUri
        every { Uri.parse("klaviyotest://detail/1") } returns mockk()
        val extras = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.url" to "klaviyotest://detail/1",
            "com.klaviyo.Button Link" to "klaviyotest://order/123"
        )

        assertEquals(buttonUri, Klaviyo.getKlaviyoDeepLink(mockIntent(extras)))
    }

    @Test
    fun `getKlaviyoDeepLink reads a button link when the notification body has no url`() {
        // An open_app body with a deep_link button: there is no com.klaviyo.url to fall back on.
        val buttonUri = mockk<Uri>()
        every { Uri.parse("klaviyotest://order/123") } returns buttonUri
        val extras = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.Button Link" to "klaviyotest://order/123"
        )

        assertEquals(buttonUri, Klaviyo.getKlaviyoDeepLink(mockIntent(extras)))
    }

    @Test
    fun `getKlaviyoDeepLink ignores a blank payload url`() {
        // toUri() happily returns a non-null, useless Uri for whitespace, so an empty check alone
        // would hand the host a link to navigate to.
        val extras = mapOf(
            "com.klaviyo._k" to requireNotNull(stubIntentExtras["com.klaviyo._k"]),
            "com.klaviyo.url" to "   "
        )

        assertEquals(null, Klaviyo.getKlaviyoDeepLink(mockIntent(extras)))
    }

    @Test
    fun `getKlaviyoDeepLink returns null for a klaviyo intent carrying no link`() {
        assertEquals(null, Klaviyo.getKlaviyoDeepLink(mockIntent(stubIntentExtras)))
    }

    @Test
    fun `getKlaviyoDeepLink returns null for non-klaviyo and null intents`() {
        // Safe to call unconditionally from onCreate/onNewIntent, so a host app's own deep link
        // must not be mistaken for a Klaviyo one.
        val testUri = mockk<Uri>()

        assertEquals(null, Klaviyo.getKlaviyoDeepLink(null))
        assertEquals(null, Klaviyo.getKlaviyoDeepLink(mockIntent(mapOf("other" to "x"), testUri)))
    }

    private val previewToken = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123-_6"

    private fun previewPushExtras(keyValuePairs: String) = mapOf(
        "com.klaviyo._k" to "{}",
        "com.klaviyo.key_value_pairs" to keyValuePairs
    )

    private fun registerPreviewSink(): FormsPreviewTokenSink =
        mockk<FormsPreviewTokenSink>(relaxed = true).also {
            Registry.register<FormsPreviewTokenSink>(
                it
            )
        }

    @Test
    fun `opened push with a preview token in key_value_pairs starts the preview and strips it from tracking`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val intent = mockIntent(
            previewPushExtras("""{"klaviyo-forms-preview":"$previewToken","other":"1"}""")
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        verify {
            intent.putExtra(
                "com.klaviyo.key_value_pairs",
                match<String> { !it.contains(previewToken) }
            )
        }
        val keyValuePairs = eventSlot.captured[EventKey.CUSTOM("key_value_pairs")] as Map<*, *>
        assertEquals(mapOf("other" to "1"), keyValuePairs)
        assertTrue(eventSlot.captured.toMap().values.none { it.toString().contains(previewToken) })
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push falls back to the deep link preview parameter and strips it from the link`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val link = "klaviyotest://home?klaviyo-forms-preview=$previewToken&x=1"
        val linkUri = mockk<Uri>().also { every { it.toString() } returns link }
        val stripped = mockk<Uri>()
        every { Uri.parse("klaviyotest://home?x=1") } returns stripped
        val (intent) = recordingIntent(
            mapOf("com.klaviyo._k" to "{}", "com.klaviyo.url" to link),
            linkUri
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        verify { intent.data = stripped }
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    /**
     * An intent whose extras are backed by a mutable map, so `putExtra` is visible to the event
     * built from the same intent.
     */
    private fun recordingIntent(extras: Map<String, String>, uri: Uri? = null): Pair<Intent, Map<String, String>> {
        val store = extras.toMutableMap()
        val intent = mockIntent(store, uri)
        every { intent.putExtra(any<String>(), any<String>()) } answers {
            store[firstArg()] = secondArg()
            intent
        }
        every { intent.removeExtra(any()) } answers {
            store.remove(firstArg<String>())
            Unit
        }
        return intent to store
    }

    private fun assertNoToken(event: Event) =
        assertTrue(event.toMap().values.none { it.toString().contains(previewToken) })

    @Test
    fun `opened push with the token only in the payload url extra starts the preview and strips it`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.url" to "klaviyotest://home?klaviyo-forms-preview=$previewToken&x=1"
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        assertEquals("klaviyotest://home?x=1", extras["com.klaviyo.url"])
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push strips the token from a button link extra`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.url" to "klaviyotest://body",
                "com.klaviyo.Button Link" to "klaviyotest://order?klaviyo-forms-preview=$previewToken"
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        assertEquals("klaviyotest://order", extras["com.klaviyo.Button Link"])
        assertEquals("klaviyotest://body", extras["com.klaviyo.url"])
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push strips the token from a link with a raw space or pipe in another parameter`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.url" to "klaviyotest://home?a=x y&b=1|2&klaviyo-forms-preview=$previewToken"
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        assertEquals("klaviyotest://home?a=x y&b=1|2", extras["com.klaviyo.url"])
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push with invalid key_value_pairs JSON containing the token leaks it nowhere`() {
        registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val invalidJson = """{"klaviyo-forms-preview":"$previewToken""""

        Klaviyo.handlePush(mockIntent(previewPushExtras(invalidJson)))

        verify { spyLog.warning(any(), any()) }
        verify(exactly = 0) { spyLog.warning(match { it.contains(previewToken) }, any()) }
        verify(exactly = 0) { spyLog.debug(match { it.contains(previewToken) }, any()) }
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push removes a malformed key_value_pairs extra from the intent and the event`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            previewPushExtras("""{"klaviyo-forms-preview":"$previewToken"""")
        )

        Klaviyo.handlePush(intent)

        assertFalse(extras.containsKey("com.klaviyo.key_value_pairs"))
        assertFalse(eventSlot.captured.toMap().containsKey("key_value_pairs"))
        verify(exactly = 0) { sink.startPreview(any()) }
        verifyOpenedPushEventEnqueued()
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push never tracks malformed key_value_pairs that escape the preview key`() {
        registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val escapedKeyJson = """{"klaviyo\u002dforms-preview":"$previewToken""""

        Klaviyo.handlePush(mockIntent(previewPushExtras(escapedKeyJson)))

        assertFalse(eventSlot.captured.toMap().containsKey("key_value_pairs"))
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push leaves valid key_value_pairs without the preview key unchanged`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val keyValuePairs = """{ "other" : "1" }"""
        val (intent, extras) = recordingIntent(previewPushExtras(keyValuePairs))

        Klaviyo.handlePush(intent)

        assertEquals(keyValuePairs, extras["com.klaviyo.key_value_pairs"])
        assertEquals(
            mapOf("other" to "1"),
            eventSlot.captured[EventKey.CUSTOM("key_value_pairs")]
        )
        verify(exactly = 0) { sink.startPreview(any()) }
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push with the token in a web_url strips it from the event and the browser extra`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.web_url" to "https://example.com/p?klaviyo-forms-preview=$previewToken&x=1",
                Constants.BROWSER_URL_EXTRA to "https://example.com/p?klaviyo-forms-preview=$previewToken&x=1"
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        assertEquals("https://example.com/p?x=1", extras["com.klaviyo.web_url"])
        assertEquals("https://example.com/p?x=1", extras[Constants.BROWSER_URL_EXTRA])
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push with the token only in the browser extra starts the preview and strips it`() {
        val sink = registerPreviewSink()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                Constants.BROWSER_URL_EXTRA to "https://example.com/p?klaviyo-forms-preview=$previewToken"
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        assertEquals("https://example.com/p", extras[Constants.BROWSER_URL_EXTRA])
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push strips the token from an open_url button's browser extra and link`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val buttonUrl = "https://example.com/offer?klaviyo-forms-preview=$previewToken"
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.Button Link" to buttonUrl,
                Constants.BROWSER_URL_EXTRA to buttonUrl
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        assertEquals("https://example.com/offer", extras["com.klaviyo.Button Link"])
        assertEquals("https://example.com/offer", extras[Constants.BROWSER_URL_EXTRA])
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `two intent copies of one delivery start the preview once and are both stripped`() {
        val sink = registerPreviewSink()
        val extras = mapOf(
            "com.klaviyo._k" to """{"tm":"preview-copies-delivery"}""",
            "com.klaviyo.url" to "klaviyotest://home?klaviyo-forms-preview=$previewToken"
        )
        val (first, firstExtras) = recordingIntent(extras)
        val (second, secondExtras) = recordingIntent(extras)

        Klaviyo.handlePush(first)
        Klaviyo.handlePush(second)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        verifyOpenedPushEventEnqueued()
        assertEquals("klaviyotest://home", firstExtras["com.klaviyo.url"])
        assertEquals("klaviyotest://home", secondExtras["com.klaviyo.url"])
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push with a malformed preview token strips it without starting a preview`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()

        Klaviyo.handlePush(
            mockIntent(previewPushExtras("""{"klaviyo-forms-preview":"short-token"}"""))
        )

        verify(exactly = 0) { sink.startPreview(any()) }
        verify { spyLog.warning(not(match { it.contains("short-token") }), null) }
        assertEquals(
            emptyMap<String, Any>(),
            eventSlot.captured[EventKey.CUSTOM("key_value_pairs")]
        )
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push with a preview token is still tracked when forms is not installed`() {
        Registry.unregister<FormsPreviewTokenSink>()
        val eventSlot = captureOpenedPushEvent()

        Klaviyo.handlePush(
            mockIntent(previewPushExtras("""{"klaviyo-forms-preview":"$previewToken"}"""))
        )

        verifyOpenedPushEventEnqueued()
        assertTrue(eventSlot.captured.toMap().values.none { it.toString().contains(previewToken) })
    }

    @Test
    fun `opened push strips the token from action button urls in the tracked action_buttons`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.key_value_pairs" to """{"klaviyo-forms-preview":"$previewToken"}""",
                "com.klaviyo.action_buttons" to
                    """[{"id":"a","label":"Shop","action":"deep_link",""" +
                    """"url":"klaviyotest://shop?klaviyo-forms-preview=$previewToken&x=1"}]"""
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        val buttons = JSONArray(extras["com.klaviyo.action_buttons"])
        assertEquals("klaviyotest://shop?x=1", buttons.getJSONObject(0).getString("url"))
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `body tap strips a token found only on an untapped action button url`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.url" to "klaviyotest://body",
                "com.klaviyo.action_buttons" to
                    """[{"id":"a","label":"Shop","action":"deep_link",""" +
                    """"url":"klaviyotest://shop?klaviyo-forms-preview=$previewToken"}]"""
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 0) { sink.startPreview(any()) }
        val buttons = JSONArray(extras["com.klaviyo.action_buttons"])
        assertEquals("klaviyotest://shop", buttons.getJSONObject(0).getString("url"))
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `opened push removes a malformed action_buttons extra from the intent and the event`() {
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.action_buttons" to
                    """[{"url":"klaviyotest://shop?klaviyo-forms-preview=$previewToken"""
            )
        )

        Klaviyo.handlePush(intent)

        assertFalse(extras.containsKey("com.klaviyo.action_buttons"))
        assertNull(eventSlot.captured[EventKey.CUSTOM("action_buttons")])
        assertNoToken(eventSlot.captured)
    }

    @Test
    fun `opened push with a non-string key_value_pairs token falls back to the link token`() {
        val sink = registerPreviewSink()
        val eventSlot = captureOpenedPushEvent()
        val (intent, extras) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to "{}",
                "com.klaviyo.key_value_pairs" to """{"klaviyo-forms-preview":123,"other":"1"}""",
                "com.klaviyo.url" to "klaviyotest://home?klaviyo-forms-preview=$previewToken"
            )
        )

        Klaviyo.handlePush(intent)

        verify(exactly = 1) { sink.startPreview(previewToken) }
        assertEquals("klaviyotest://home", extras["com.klaviyo.url"])
        assertEquals(mapOf("other" to "1"), eventSlot.captured[EventKey.CUSTOM("key_value_pairs")])
        assertNoToken(eventSlot.captured)
        Registry.unregister<FormsPreviewTokenSink>()
    }

    @Test
    fun `action button tap on a preview push starts the preview and dismisses the notification`() {
        val sink = registerPreviewSink()
        val notificationManager = mockk<NotificationManagerCompat>(relaxed = true)
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(any()) } returns notificationManager
        val tag = "preview-button-tag"
        val (intent) = recordingIntent(
            mapOf(
                "com.klaviyo._k" to """{"tm":"preview-button-tap"}""",
                "com.klaviyo.Button Link" to "klaviyotest://order?klaviyo-forms-preview=$previewToken",
                Constants.NOTIFICATION_TAG_EXTRA to tag
            )
        )

        try {
            Klaviyo.handlePush(intent)

            verify(exactly = 1) { sink.startPreview(previewToken) }
            verify(exactly = 1) { notificationManager.cancel(tag, Constants.NOTIFICATION_ID) }
        } finally {
            unmockkStatic(NotificationManagerCompat::class)
            Registry.unregister<FormsPreviewTokenSink>()
        }
    }

    @Test
    fun `opened push without a preview token does not touch forms`() {
        val sink = registerPreviewSink()

        Klaviyo.handlePush(mockIntent(previewPushExtras("""{"other":"1"}""")))

        verify(exactly = 0) { sink.startPreview(any()) }
        Registry.unregister<FormsPreviewTokenSink>()
    }
}
