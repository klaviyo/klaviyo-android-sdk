package com.klaviyo.analytics

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.klaviyo.analytics.Klaviyo.isKlaviyoUniversalLink
import com.klaviyo.analytics.Klaviyo.isKlaviyoUniversalLinkIntent
import com.klaviyo.analytics.linking.DeepLinkHandler
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ResolveDestinationCallback
import com.klaviyo.analytics.networking.requests.ResolveDestinationResult
import com.klaviyo.analytics.state.KlaviyoState
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateSideEffects
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.MissingAPIKey
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyAll
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class KlaviyoTest : BaseTest() {

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

        fun mockIntent(payload: Map<String, String>): Intent {
            // Mocking an intent to return the stub push payload...
            val intent = mockk<Intent>()
            val bundle = mockk<Bundle>()
            every { intent.extras } returns bundle
            every { bundle.keySet() } returns payload.keys
            every { intent.getStringExtra(any()) } answers { call -> payload[call.invocation.args[0]] }
            every {
                bundle.getString(
                    any(),
                    String()
                )
            } answers { call -> payload[call.invocation.args[0]] }

            return intent
        }

        private const val TRACKING_URL = "https://trk.klaviyo.com/u/slug"

        private const val DESTINATION_URL = "https://www.klaviyo.com/some/path?query=param"

        private val mockTrackUri = mockk<Uri>().also {
            every { it.scheme } returns "https"
            every { it.path } returns "/u/slug"
            every { it.toString() } returns TRACKING_URL
        }

        private val mockDestinationUri = mockk<Uri>().also {
            every { it.scheme } returns "https"
            every { it.path } returns "/some/path"
            every { it.toString() } returns DESTINATION_URL
        }

        private val validHttpUri = mockk<Uri>().apply {
            every { scheme } returns "http"
            every { path } returns "/u/test"
        }

        private val invalidSchemeUri = mockk<Uri>().apply {
            every { scheme } returns "ftp"
            every { path } returns "/u/slug"
        }

        private val invalidPathUri = mockk<Uri>().apply {
            every { scheme } returns "https"
            every { path } returns "/other/path"
        }

        private val nullPathUri = mockk<Uri>().apply {
            every { scheme } returns "https"
            every { path } returns null
        }

        private val mockTrackingUriIntent = mockk<Intent>().apply {
            every { data } returns mockTrackUri
        }

        private val mockDestinationUriIntent = mockk<Intent>().apply {
            every { data } returns mockDestinationUri
        }

        private val mockNullDataIntent = mockk<Intent>().apply {
            every { data } returns null
        }
    }

    private val capturedProfile = slot<Profile>()
    private val mockApiClient: ApiClient = mockk<ApiClient>().apply {
        every { startService() } returns Unit
        every { onApiRequest(any(), any()) } returns Unit
        every { offApiRequest(any()) } returns Unit
        every { enqueueProfile(capture(capturedProfile)) } returns Unit
        every { enqueueEvent(any(), any()) } returns Unit
        every { enqueuePushToken(any(), any()) } returns Unit
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

        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = mockContext
        )
    }

    @After
    override fun cleanup() {
        unmockkAll()
        Registry.unregister<Config>()
        Registry.unregister<State>()
        Registry.unregister<StateSideEffects>()
        Registry.unregister<ApiClient>()
        super.cleanup()
        Registry.unregister<Config>()
        unmockDeviceProperties()
    }

    @Test
    fun `Registered mock api`() {
        assertEquals(mockApiClient, Registry.get<ApiClient>())
        verify { mockApiClient.startService() }
    }

    @Test
    fun `Registered state and side effects`() {
        assertTrue(Registry.get<State>() is KlaviyoState)
        assertNotNull(Registry.getOrNull<StateSideEffects>())
    }

    @Test
    fun `Initialize properly creates new config service and attaches lifecycle listeners`() {
        val expectedListener = Registry.lifecycleCallbacks
        val expectedConfigListener = Registry.componentCallbacks
        verifyAll {
            mockBuilder.apiKey(API_KEY)
            mockBuilder.applicationContext(mockContext)
            mockBuilder.build()
            mockApplication.unregisterActivityLifecycleCallbacks(match { it == expectedListener })
            mockApplication.registerActivityLifecycleCallbacks(match { it == expectedListener })
            mockApplication.unregisterComponentCallbacks(match { it == expectedConfigListener })
            mockApplication.registerComponentCallbacks(match { it == expectedConfigListener })
        }
    }

    private fun verifyProfileDebounced() {
        staticClock.execute(debounceTime.toLong())
        verify(exactly = 1) { mockApiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Fluent profile updates are debounced`() {
        Klaviyo.setExternalId(EXTERNAL_ID)
            .setEmail(EMAIL)
            .setPhoneNumber(PHONE)

        verify(exactly = 0) { mockApiClient.enqueueProfile(any()) }
        verifyProfileDebounced()
    }

    @Test
    fun `Profile debounce preserves all properties`() {
        val stubMiddleNameKey = ProfileKey.CUSTOM("middle_name")
        val stubFirstName = "Kermit"
        val stubMiddleName = "The"
        val stubLastName = "Frog"
        Klaviyo.setExternalId(EXTERNAL_ID)
            .setEmail(EMAIL)
            .setPhoneNumber(PHONE)
            .setProfileAttribute(ProfileKey.FIRST_NAME, stubFirstName)
            .setProfileAttribute(ProfileKey.LAST_NAME, stubLastName)
            .setProfileAttribute(stubMiddleNameKey, stubMiddleName)

        verify(exactly = 0) { mockApiClient.enqueueProfile(any()) }
        verifyProfileDebounced()
        assert(capturedProfile.isCaptured)
        val profile = capturedProfile.captured
        assertEquals(EXTERNAL_ID, profile.externalId)
        assertEquals(EMAIL, profile.email)
        assertEquals(PHONE, profile.phoneNumber)
        assertEquals(stubFirstName, profile[ProfileKey.FIRST_NAME])
        assertEquals(stubLastName, profile[ProfileKey.LAST_NAME])
        assertEquals(stubMiddleName, profile[stubMiddleNameKey])
    }

    @Test
    fun `Whitespace is trimmed off of profile identifiers for fluent setters`() {
        Klaviyo.setExternalId("\t$EXTERNAL_ID \n")
            .setEmail("$EMAIL \t\n")
            .setPhoneNumber(" $PHONE \t\n")

        verifyProfileDebounced()
        val profile = capturedProfile.captured
        assertEquals(EXTERNAL_ID, profile.externalId)
        assertEquals(EMAIL, profile.email)
        assertEquals(PHONE, profile.phoneNumber)
    }

    @Test
    fun `Whitespace is trimmed off of profile identifiers for setProfile`() {
        Klaviyo.setProfile(
            Profile(
                externalId = "\t$EXTERNAL_ID \n",
                email = "$EMAIL \t\n",
                phoneNumber = " $PHONE \t\n"
            )
        )

        verifyProfileDebounced()
        val profile = capturedProfile.captured
        assertEquals(EXTERNAL_ID, profile.externalId)
        assertEquals(EMAIL, profile.email)
        assertEquals(PHONE, profile.phoneNumber)
    }

    @Test
    fun `Empty identifiers are ignored with warning`() {
        Klaviyo.setProfile(
            Profile(
                externalId = EXTERNAL_ID,
                email = EMAIL,
                phoneNumber = PHONE
            )
        )

        verifyProfileDebounced()
        val profile = capturedProfile.captured
        assertEquals(EXTERNAL_ID, profile.externalId)
        assertEquals(EMAIL, profile.email)
        assertEquals(PHONE, profile.phoneNumber)

        Klaviyo.setExternalId("")
        Klaviyo.setEmail("")
        Klaviyo.setPhoneNumber("")

        verifyProfileDebounced() // Should not have enqueued a new request
        verify(exactly = 3) { spyLog.warning(any(), null) }
        assertEquals(EXTERNAL_ID, Registry.get<State>().externalId)
        assertEquals(EMAIL, Registry.get<State>().email)
        assertEquals(PHONE, Registry.get<State>().phoneNumber)
    }

    @Test
    fun `Unchanged profile identifiers do not enqueue new API requests`() {
        Klaviyo.setProfile(
            Profile(
                externalId = EXTERNAL_ID,
                email = EMAIL,
                phoneNumber = PHONE
            )
        )

        verifyProfileDebounced()
        val profile = capturedProfile.captured
        assertEquals(EXTERNAL_ID, profile.externalId)
        assertEquals(EMAIL, profile.email)
        assertEquals(PHONE, profile.phoneNumber)

        Klaviyo.setExternalId(EXTERNAL_ID)
        Klaviyo.setEmail(EMAIL)
        Klaviyo.setPhoneNumber(PHONE)

        // We should not have enqueued a new request, so still should only have been called once
        verifyProfileDebounced()
    }

    @Test
    fun `setProfile is debounced`() {
        Klaviyo.setProfile(Profile().setEmail(EMAIL))

        verify(exactly = 0) { mockApiClient.enqueueProfile(any()) }
        verifyProfileDebounced()
    }

    @Test
    fun `setProfile merges into an anonymous profile`() {
        val anonId = Registry.get<State>().anonymousId

        Klaviyo.setProfile(Profile().setEmail(EMAIL))

        assertEquals(EMAIL, Registry.get<State>().email)
        assertEquals(anonId, Registry.get<State>().anonymousId)
    }

    @Test
    fun `setProfile resets current profile and passes new identifiers to UserInfo`() {
        Registry.get<State>().email = "other"
        val anonId = Registry.get<State>().anonymousId
        val newProfile = Profile().setExternalId(EXTERNAL_ID)

        Klaviyo.setProfile(newProfile)

        assertEquals(EXTERNAL_ID, Registry.get<State>().externalId)
        assertNull(Registry.get<State>().email)
        assertNotEquals(anonId, Registry.get<State>().anonymousId)
    }

    @Test
    fun `Sets user external ID into info`() {
        Klaviyo.setExternalId(EXTERNAL_ID)

        assertEquals(EXTERNAL_ID, Registry.get<State>().externalId)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets user email into info`() {
        Klaviyo.setEmail(EMAIL)

        assertEquals(EMAIL, Registry.get<State>().email)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets user phone into info`() {
        Klaviyo.setPhoneNumber(PHONE)

        assertEquals(PHONE, Registry.get<State>().phoneNumber)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets an arbitrary user property`() {
        val stubName = "Gonzo"
        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, stubName)
        assertEquals(
            stubName,
            Registry.get<State>().getAsProfile(withAttributes = true)[ProfileKey.FIRST_NAME]
        )
    }

    @Test
    fun `Enqueues API call for an arbitrary user property`() {
        val stubName = "Gonzo"
        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, stubName)

        verifyProfileDebounced()
        assert(capturedProfile.isCaptured)
        assertEquals(stubName, capturedProfile.captured[ProfileKey.FIRST_NAME])
    }

    @Test
    fun `Sets a serializable user property`() {
        val bestNumber = 4
        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, bestNumber)

        verifyProfileDebounced()
        assert(capturedProfile.isCaptured)
        assertEquals(bestNumber, capturedProfile.captured[ProfileKey.FIRST_NAME])
    }

    @Test
    fun `Serialiazable not in identifiers still gets debounced`() {
        val bestString = ""
        val key = ProfileKey.CUSTOM("danKey")
        Klaviyo.setProfileAttribute(key, bestString)

        verifyProfileDebounced()
        assert(capturedProfile.isCaptured)
        assertEquals(bestString, capturedProfile.captured[key])
    }

    @Test
    fun `Resets user info`() {
        val anonId = Registry.get<State>().anonymousId
        Registry.get<State>().email = EMAIL
        Registry.get<State>().phoneNumber = PHONE
        Registry.get<State>().externalId = EXTERNAL_ID

        Klaviyo.resetProfile()

        assertNotEquals(anonId, Registry.get<State>().anonymousId)
        assertNull(Registry.get<State>().email)
        assertNull(Registry.get<State>().phoneNumber)
        assertNull(Registry.get<State>().externalId)

        // Resetting profile flushes the queue immediately
        verify(exactly = 1) { mockApiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Reset removes push token from store`() {
        Registry.get<State>().email = EMAIL
        spyDataStore.store("push_token", PUSH_TOKEN)

        Klaviyo.resetProfile()

        assertNull(null, Registry.get<State>().email)
    }

    @Test
    fun `Gets identifiers out of user info`() {
        assertNull(Klaviyo.getEmail())
        assertNull(Klaviyo.getPhoneNumber())
        assertNull(Klaviyo.getExternalId())

        Registry.get<State>().email = EMAIL
        Registry.get<State>().phoneNumber = PHONE
        Registry.get<State>().externalId = EXTERNAL_ID

        assertEquals(EMAIL, Klaviyo.getEmail())
        assertEquals(PHONE, Klaviyo.getPhoneNumber())
        assertEquals(EXTERNAL_ID, Klaviyo.getExternalId())
    }

    @Test
    fun `Stores push token and Enqueues a push token API call`() {
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, spyDataStore.fetch("push_token"))

        verify(exactly = 1) {
            mockApiClient.enqueuePushToken(PUSH_TOKEN, any())
        }
    }

    @Test
    fun `Push token request is ignored if state has not changed`() {
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, spyDataStore.fetch("push_token"))

        verify(exactly = 1) {
            mockApiClient.enqueuePushToken(PUSH_TOKEN, any())
        }

        Klaviyo.setPushToken(PUSH_TOKEN)

        verify(exactly = 1) {
            mockApiClient.enqueuePushToken(PUSH_TOKEN, any())
        }
    }

    @Test
    fun `Push token request is repeated if state has changed`() {
        every { DeviceProperties.backgroundDataEnabled } returns true
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, spyDataStore.fetch("push_token"))

        verify(exactly = 1) {
            mockApiClient.enqueuePushToken(PUSH_TOKEN, any())
        }

        every { DeviceProperties.backgroundDataEnabled } returns false
        Klaviyo.setPushToken(PUSH_TOKEN)

        verify(exactly = 2) {
            mockApiClient.enqueuePushToken(PUSH_TOKEN, any())
        }
    }

    @Test
    fun `Push token request is made if profile identifiers change and token is set`() {
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, spyDataStore.fetch("push_token"))

        verify(exactly = 1) {
            mockApiClient.enqueuePushToken(PUSH_TOKEN, any())
        }

        Klaviyo.setEmail(EMAIL)
            .setPhoneNumber(PHONE)
            .setExternalId(EXTERNAL_ID)

        staticClock.execute(debounceTime.toLong())
        verify(exactly = 2) { mockApiClient.enqueuePushToken(PUSH_TOKEN, any()) }
    }

    @Test
    fun `Push token request is made if profile changes and token is set`() {
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, spyDataStore.fetch("push_token"))

        verify(exactly = 1) {
            mockApiClient.enqueuePushToken(PUSH_TOKEN, any())
        }

        Klaviyo.setProfile(Profile().setEmail(EMAIL))

        staticClock.execute(debounceTime.toLong())
        verify(exactly = 2) { mockApiClient.enqueuePushToken(PUSH_TOKEN, any()) }
    }

    @Test
    fun `Push token request is made for profile attributes when token is set`() {
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, spyDataStore.fetch("push_token"))

        verify(exactly = 1) {
            mockApiClient.enqueuePushToken(PUSH_TOKEN, any())
        }

        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, "Larry")
        Klaviyo.setProfileAttribute(ProfileKey.LAST_NAME, "David")

        staticClock.execute(debounceTime.toLong())
        verify(exactly = 2) { mockApiClient.enqueuePushToken(PUSH_TOKEN, any()) }
    }

    @Test
    fun `Retrieve saved push token from data store`() {
        assertNull(Klaviyo.getPushToken())
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, Klaviyo.getPushToken())
    }

    @Test
    fun `Fetches push token from persistent store`() {
        spyDataStore.store("push_token", PUSH_TOKEN)
        assertEquals(Klaviyo.getPushToken(), PUSH_TOKEN)
    }

    @Test
    fun `Handling opened push Intent enqueues $opened_push API Call`() {
        // Handle push intent
        Klaviyo.handlePush(mockIntent(stubIntentExtras))

        verify { mockApiClient.enqueueEvent(any(), any()) }
    }

    @Test
    fun `Non-klaviyo push payload is ignored`() {
        // doesn't have _k, klaviyo tracking params
        Klaviyo.handlePush(mockIntent(mapOf("com.other.package.message" to "3rd party push")))
        Klaviyo.handlePush(null)

        verify(inverse = true) { mockApiClient.enqueueEvent(any(), any()) }
    }

    @Test
    fun `Enqueue an event API call`() {
        val stubEvent = Event(EventMetric.VIEWED_PRODUCT).also { it[EventKey.VALUE] = 1 }
        Klaviyo.createEvent(stubEvent)

        verify(exactly = 1) {
            mockApiClient.enqueueEvent(stubEvent, any())
        }
    }

    @Test
    fun `Enqueue an event API call conveniently`() {
        Klaviyo.createEvent(EventMetric.VIEWED_PRODUCT)

        verify(exactly = 1) {
            mockApiClient.enqueueEvent(match { it.metric == EventMetric.VIEWED_PRODUCT }, any())
        }
    }

    @Test
    fun `Initializing State and side effects is idempotent`() {
        // Since the test setup already initializes Klaviyo:
        val initialState = Registry.get<State>()
        val spyState = spyk(initialState)
        val initialSideEffects = Registry.get<StateSideEffects>()
        Registry.register<State>(spyState)

        // Re-reinitialize multiple times:
        Klaviyo.initialize(
            apiKey = "keyTwo",
            applicationContext = mockContext
        )

        Klaviyo.initialize(
            apiKey = "newKey",
            applicationContext = mockContext
        )

        // State and SideEffects should not change in registry, nor register additional observers
        assertEquals(spyState, Registry.get<State>())
        assertEquals(initialSideEffects, Registry.get<StateSideEffects>())
        verify(exactly = 1) { mockApiClient.onApiRequest(false, any()) }
    }

    @Test
    fun `registering a deep link handler`() {
        assertNull(null, Registry.getOrNull<DeepLinkHandler>())
        Klaviyo.registerDeepLinkHandler() {}
        assertNotNull(Registry.get<DeepLinkHandler>())
    }

    @Test
    fun `handleUniversalTrackingLink handles a valid tracking url and returns true`() {
        val slot = slot<ResolveDestinationCallback>()
        var called = false

        every { Uri.parse(TRACKING_URL) } returns mockTrackUri
        every { Uri.parse(DESTINATION_URL) } returns mockDestinationUri
        every { mockApiClient.resolveDestinationUrl(any(), any(), capture(slot)) } returns Unit

        Klaviyo.registerDeepLinkHandler { _ -> called = true }
        assertTrue(Klaviyo.handleUniversalTrackingLink(mockTrackingUriIntent))

        // Should have called the registered deep link handler
        assertTrue(slot.isCaptured)
        slot.captured.invoke(ResolveDestinationResult.Success(URL(DESTINATION_URL), TRACKING_URL))
        assertTrue(called)
    }

    @Test
    fun `handleUniversalTrackingLink handles ResolveDestinationResult Unavailable`() {
        val slot = slot<ResolveDestinationCallback>()

        every { Uri.parse(TRACKING_URL) } returns mockTrackUri
        every { mockApiClient.resolveDestinationUrl(any(), any(), capture(slot)) } returns Unit

        Klaviyo.handleUniversalTrackingLink(mockTrackingUriIntent)

        assertTrue(slot.isCaptured)
        slot.captured.invoke(ResolveDestinationResult.Unavailable(TRACKING_URL))

        verify { spyLog.warning(match { it.contains("Destination URL unavailable") }, null) }
    }

    @Test
    fun `handleUniversalTrackingLink handles ResolveDestinationResult Failure`() {
        val slot = slot<ResolveDestinationCallback>()

        every { Uri.parse(TRACKING_URL) } returns mockTrackUri
        every { mockApiClient.resolveDestinationUrl(any(), any(), capture(slot)) } returns Unit

        Klaviyo.handleUniversalTrackingLink(TRACKING_URL)

        assertTrue(slot.isCaptured)
        slot.captured.invoke(ResolveDestinationResult.Failure(TRACKING_URL))

        verify { spyLog.error(match { it.contains("Failed to resolve destination URL") }, null) }
    }

    @Test
    fun `handleUniversalTrackingLink fails gracefully if uninitialized`() {
        val slot = slot<ResolveDestinationCallback>()

        every { Uri.parse(TRACKING_URL) } returns mockTrackUri
        every { mockApiClient.resolveDestinationUrl(any(), any(), capture(slot)) } throws MissingAPIKey()

        assertEquals(false, Klaviyo.handleUniversalTrackingLink(TRACKING_URL))

        assertTrue(slot.isCaptured)
    }

    @Test
    fun `handleUniversalTrackingLink returns false for non-Klaviyo Intent`() {
        assertEquals(false, Klaviyo.handleUniversalTrackingLink(mockDestinationUriIntent))
    }

    @Test
    fun `handleUniversalTrackingLink returns false for non-Klaviyo Url`() {
        assertEquals(false, Klaviyo.handleUniversalTrackingLink(DESTINATION_URL))
    }

    @Test
    fun `handleUniversalTrackingLink returns false for missing and Intent with no Url`() {
        assertEquals(false, Klaviyo.handleUniversalTrackingLink(null))
        assertEquals(false, Klaviyo.handleUniversalTrackingLink(mockNullDataIntent))
    }

    @Test
    fun `handleUniversalTrackingLink returns false for invalid URLs`() {
        assertEquals(false, Klaviyo.handleUniversalTrackingLink("not a url"))
    }

    @Test
    fun `isKlaviyoUniversalLinkIntent extension property validation`() {
        assertTrue(mockTrackingUriIntent.isKlaviyoUniversalLinkIntent)
        assertEquals(false, mockDestinationUriIntent.isKlaviyoUniversalLinkIntent)
        assertEquals(false, mockNullDataIntent.isKlaviyoUniversalLinkIntent)
    }

    @Test
    fun `isKlaviyoUniversalLink Uri extension property validation`() {
        assertTrue(mockTrackUri.isKlaviyoUniversalLink)
        assertTrue(validHttpUri.isKlaviyoUniversalLink)
        assertEquals(false, invalidSchemeUri.isKlaviyoUniversalLink)
        assertEquals(false, invalidPathUri.isKlaviyoUniversalLink)
        assertEquals(false, nullPathUri.isKlaviyoUniversalLink)
    }
}
