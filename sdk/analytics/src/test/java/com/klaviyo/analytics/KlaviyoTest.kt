package com.klaviyo.analytics

import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent
import android.os.Bundle
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.state.UserState
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
            every { bundle.getString(any(), String()) } answers { call -> payload[call.invocation.args[0]] }

            return intent
        }
    }

    private val capturedProfile = slot<Profile>()
    private val debounceTime = 5
    private val apiClientMock: ApiClient = mockk<ApiClient>().apply {
        Registry.register<ApiClient>(this)
        every { onApiRequest(any(), any()) } returns Unit
        every { enqueueProfile(capture(capturedProfile)) } returns Unit
        every { enqueueEvent(any(), any()) } returns Unit
        every { enqueuePushToken(any(), any()) } returns Unit
    }

    private val builderMock = mockk<Config.Builder>().apply {
        every { apiKey(any()) } returns this
        every { applicationContext(any()) } returns this
        every { build() } returns configMock
    }

    private val mockApplication = mockk<Application>().apply {
        every { contextMock.applicationContext } returns this
        every { unregisterActivityLifecycleCallbacks(any()) } returns Unit
        every { registerActivityLifecycleCallbacks(any()) } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()
        every { Registry.configBuilder } returns builderMock
        every { configMock.debounceInterval } returns debounceTime
        DevicePropertiesTest.mockDeviceProperties()
        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = contextMock
        )
    }

    @After
    override fun cleanup() {
        Registry.get<UserState>().reset()
        super.cleanup()
        DevicePropertiesTest.unmockDeviceProperties()
    }

    @Test
    fun `Registered mock api`() {
        assertEquals(apiClientMock, Registry.get<ApiClient>())
    }

    @Test
    fun `Initialize properly creates new config service and attaches lifecycle listeners`() {
        val expectedListener = Registry.lifecycleCallbacks
        verifyAll {
            builderMock.apiKey(API_KEY)
            builderMock.applicationContext(contextMock)
            builderMock.build()
            mockApplication.unregisterActivityLifecycleCallbacks(match { it == expectedListener })
            mockApplication.registerActivityLifecycleCallbacks(match { it == expectedListener })
        }
    }

    @Test
    fun `Klaviyo does not make core lifecycle callbacks service publicly available`() {
        val mockLifecycleCallbacks = mockk<ActivityLifecycleCallbacks>()
        every { Registry.lifecycleCallbacks } returns mockLifecycleCallbacks
        assertNotEquals(mockLifecycleCallbacks, Klaviyo.lifecycleCallbacks)
    }

    private fun verifyProfileDebounced() {
        staticClock.execute(debounceTime.toLong())
        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Fluent profile updates are debounced`() {
        Klaviyo.setExternalId(EXTERNAL_ID)
            .setEmail(EMAIL)
            .setPhoneNumber(PHONE)

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
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

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
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
        verify(exactly = 3) { logSpy.warning(any(), null) }
        assertEquals(EXTERNAL_ID, Registry.get<UserState>().externalId)
        assertEquals(EMAIL, Registry.get<UserState>().email)
        assertEquals(PHONE, Registry.get<UserState>().phoneNumber)
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

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
        verifyProfileDebounced()
    }

    @Test
    fun `setProfile merges into an anonymous profile`() {
        val anonId = Registry.get<UserState>().anonymousId

        Klaviyo.setProfile(Profile().setEmail(EMAIL))

        assertEquals(EMAIL, Registry.get<UserState>().email)
        assertEquals(anonId, Registry.get<UserState>().anonymousId)
    }

    @Test
    fun `setProfile resets current profile and passes new identifiers to UserInfo`() {
        Registry.get<UserState>().email = "other"
        val anonId = Registry.get<UserState>().anonymousId
        val newProfile = Profile().setExternalId(EXTERNAL_ID)

        Klaviyo.setProfile(newProfile)

        assertEquals(EXTERNAL_ID, Registry.get<UserState>().externalId)
        assertNull(Registry.get<UserState>().email)
        assertNotEquals(anonId, Registry.get<UserState>().anonymousId)
    }

    @Test
    fun `Sets user external ID into info`() {
        Klaviyo.setExternalId(EXTERNAL_ID)

        assertEquals(EXTERNAL_ID, Registry.get<UserState>().externalId)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets user email into info`() {
        Klaviyo.setEmail(EMAIL)

        assertEquals(EMAIL, Registry.get<UserState>().email)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets user phone into info`() {
        Klaviyo.setPhoneNumber(PHONE)

        assertEquals(PHONE, Registry.get<UserState>().phoneNumber)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets an arbitrary user property`() {
        val stubName = "Gonzo"
        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, stubName)
        assertEquals(stubName, Registry.get<UserState>().get(true)[ProfileKey.FIRST_NAME])
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
    fun `Resets user info`() {
        val anonId = Registry.get<UserState>().anonymousId
        Registry.get<UserState>().email = EMAIL
        Registry.get<UserState>().phoneNumber = PHONE
        Registry.get<UserState>().externalId = EXTERNAL_ID

        Klaviyo.resetProfile()

        assertNotEquals(anonId, Registry.get<UserState>().anonymousId)
        assertNull(Registry.get<UserState>().email)
        assertNull(Registry.get<UserState>().phoneNumber)
        assertNull(Registry.get<UserState>().externalId)

        // Resetting profile flushes the queue immediately
        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Reset removes push token from store`() {
        Registry.get<UserState>().email = EMAIL
        dataStoreSpy.store("push_token", PUSH_TOKEN)

        Klaviyo.resetProfile()

        assertNull(null, Registry.get<UserState>().email)
        assertNull(null, dataStoreSpy.fetch("push_token"))
    }

    @Test
    fun `Gets identifiers out of user info`() {
        assertNull(Klaviyo.getEmail())
        assertNull(Klaviyo.getPhoneNumber())
        assertNull(Klaviyo.getExternalId())

        Registry.get<UserState>().email = EMAIL
        Registry.get<UserState>().phoneNumber = PHONE
        Registry.get<UserState>().externalId = EXTERNAL_ID

        assertEquals(EMAIL, Klaviyo.getEmail())
        assertEquals(PHONE, Klaviyo.getPhoneNumber())
        assertEquals(EXTERNAL_ID, Klaviyo.getExternalId())
    }

    @Test
    fun `Stores push token and Enqueues a push token API call`() {
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, dataStoreSpy.fetch("push_token"))

        verify(exactly = 1) {
            apiClientMock.enqueuePushToken(PUSH_TOKEN, any())
        }
    }

    @Test
    fun `Push token request is ignored if state has not changed`() {
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, dataStoreSpy.fetch("push_token"))

        verify(exactly = 1) {
            apiClientMock.enqueuePushToken(PUSH_TOKEN, any())
        }

        Klaviyo.setPushToken(PUSH_TOKEN)

        verify(exactly = 1) {
            apiClientMock.enqueuePushToken(PUSH_TOKEN, any())
        }
    }

    @Test
    fun `Push token request is repeated if state has changed`() {
        every { DeviceProperties.backgroundData } returns true
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, dataStoreSpy.fetch("push_token"))

        verify(exactly = 1) {
            apiClientMock.enqueuePushToken(PUSH_TOKEN, any())
        }

        every { DeviceProperties.backgroundData } returns false
        Klaviyo.setPushToken(PUSH_TOKEN)

        verify(exactly = 2) {
            apiClientMock.enqueuePushToken(PUSH_TOKEN, any())
        }
    }

    @Test
    fun `Retrieve saved push token from data store`() {
        assertNull(Klaviyo.getPushToken())
        Klaviyo.setPushToken(PUSH_TOKEN)
        assertEquals(PUSH_TOKEN, Klaviyo.getPushToken())
    }

    @Test
    fun `Fetches push token from persistent store`() {
        dataStoreSpy.store("push_token", PUSH_TOKEN)
        assertEquals(Klaviyo.getPushToken(), PUSH_TOKEN)
    }

    @Test
    fun `Handling opened push Intent enqueues $opened_push API Call`() {
        // Handle push intent
        Klaviyo.handlePush(mockIntent(stubIntentExtras))

        verify { apiClientMock.enqueueEvent(any(), any()) }
    }

    @Test
    fun `Non-klaviyo push payload is ignored`() {
        // doesn't have _k, klaviyo tracking params
        Klaviyo.handlePush(mockIntent(mapOf("com.other.package.message" to "3rd party push")))
        Klaviyo.handlePush(null)

        verify(inverse = true) { apiClientMock.enqueueEvent(any(), any()) }
    }

    @Test
    fun `Enqueue an event API call`() {
        val stubEvent = Event(EventMetric.VIEWED_PRODUCT).also { it[EventKey.VALUE] = 1 }
        Klaviyo.createEvent(stubEvent)

        verify(exactly = 1) {
            apiClientMock.enqueueEvent(stubEvent, any())
        }
    }

    @Test
    fun `Enqueue an event API call conveniently`() {
        Klaviyo.createEvent(EventMetric.VIEWED_PRODUCT)

        verify(exactly = 1) {
            apiClientMock.enqueueEvent(match { it.metric == EventMetric.VIEWED_PRODUCT }, any())
        }
    }
}
