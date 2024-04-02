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
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.StaticClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
            var gettingKey = ""
            every { intent.extras } returns bundle
            every { bundle.keySet() } returns payload.keys
            every {
                intent.getStringExtra(
                    match { s ->
                        gettingKey = s // there must be a better way to do this...
                        true
                    }
                )
            } answers { payload[gettingKey] }
            every {
                bundle.getString(
                    match { s ->
                        gettingKey = s // there must be a better way to do this...
                        true
                    },
                    String()
                )
            } answers { payload[gettingKey] }

            return intent
        }
    }

    private val capturedProfile = slot<Profile>()
    private val staticClock = StaticClock(TIME, ISO_TIME)
    private val debounceTime = 5
    private val apiClientMock: ApiClient = mockk()

    override fun setup() {
        super.setup()
        Registry.register<ApiClient> { apiClientMock }
        every { Registry.clock } returns staticClock
        every { apiClientMock.onApiRequest(any(), any()) } returns Unit
        every { apiClientMock.enqueueProfile(capture(capturedProfile)) } returns Unit
        every { apiClientMock.enqueueEvent(any(), any()) } returns Unit
        every { apiClientMock.enqueuePushToken(any(), any()) } returns Unit
        every { configMock.debounceInterval } returns debounceTime
        UserInfo.reset()
        DevicePropertiesTest.mockDeviceProperties()
    }

    @After
    fun cleanup() {
        UserInfo.reset()
        DevicePropertiesTest.unmockDeviceProperties()
    }

    @Test
    fun `Registered mock api`() {
        assertEquals(apiClientMock, Registry.get<ApiClient>())
    }

    @Test
    fun `initialize properly creates new config service and attaches lifecycle listeners`() {
        val builderMock = mockk<Config.Builder>()
        every { Registry.configBuilder } returns builderMock
        every { builderMock.apiKey(any()) } returns builderMock
        every { builderMock.applicationContext(any()) } returns builderMock
        every { builderMock.build() } returns configMock

        val mockApplication = mockk<Application>()
        every { contextMock.applicationContext } returns mockApplication.also {
            every { it.unregisterActivityLifecycleCallbacks(any()) } returns Unit
            every { it.registerActivityLifecycleCallbacks(any()) } returns Unit
        }

        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = contextMock
        )

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
    fun `setProfile is debounced`() {
        Klaviyo.setProfile(Profile().setEmail(EMAIL))

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
        verifyProfileDebounced()
    }

    @Test
    fun `SetProfile with same identifiers doesn't enqueue profile request`() {
        UserInfo.email = EMAIL
        UserInfo.phoneNumber = PHONE
        UserInfo.externalId = EXTERNAL_ID

        val profile = Profile(
            mapOf(
                ProfileKey.EMAIL to EMAIL,
                ProfileKey.PHONE_NUMBER to PHONE,
                ProfileKey.EXTERNAL_ID to EXTERNAL_ID
            )
        )

        Klaviyo.setProfile(profile)

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }

        verifyProfileDebounced()
    }

    @Test
    fun `SetProfile's fluent setters ignores empty strings and doesn't enqueue profile request`() {
        Klaviyo.setProfile(Profile().setEmail("").setPhoneNumber("").setExternalId(""))

        assertEquals(Klaviyo.getEmail(), null)
        assertEquals(Klaviyo.getPhoneNumber(), null)
        assertEquals(Klaviyo.getExternalId(), null)
    }

    @Test
    fun `setProfile merges into an anonymous profile`() {
        val anonId = UserInfo.anonymousId

        Klaviyo.setProfile(Profile().setEmail(EMAIL))

        assertEquals(EMAIL, UserInfo.email)
        assertEquals(anonId, UserInfo.anonymousId)
    }

    @Test
    fun `setProfile resets current profile and passes new identifiers to UserInfo`() {
        UserInfo.email = "other"
        val anonId = UserInfo.anonymousId
        val newProfile = Profile().setExternalId(EXTERNAL_ID)

        Klaviyo.setProfile(newProfile)

        assertEquals(EXTERNAL_ID, UserInfo.externalId)
        assertEquals("", UserInfo.email)
        assertNotEquals(anonId, UserInfo.anonymousId)
    }

    @Test
    fun `Sets user external ID into info`() {
        Klaviyo.setExternalId(EXTERNAL_ID)

        assertEquals(EXTERNAL_ID, UserInfo.externalId)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets empty external id doesn't enqueue profile request`() {
        UserInfo.externalId = EXTERNAL_ID

        Klaviyo.setExternalId("")
        assertEquals(EXTERNAL_ID, UserInfo.externalId)

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Sets external id doesn't enqueue profile request if it's the same`() {
        UserInfo.externalId = EXTERNAL_ID

        Klaviyo.setExternalId(EXTERNAL_ID)
        assertEquals(EXTERNAL_ID, UserInfo.externalId)

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Sets user email into info`() {
        Klaviyo.setEmail(EMAIL)

        assertEquals(EMAIL, UserInfo.email)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets empty email into info doesn't enqueue profile request`() {
        UserInfo.email = EMAIL

        Klaviyo.setEmail("")
        assertEquals(EMAIL, UserInfo.email)

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `setEmail does not update UserInfo when called with the same email`() {
        val initialEmail = "test@example.com"
        UserInfo.email = initialEmail

        Klaviyo.setEmail(initialEmail)

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Sets user phone into info`() {
        Klaviyo.setPhoneNumber(PHONE)

        assertEquals(PHONE, UserInfo.phoneNumber)
        verifyProfileDebounced()
    }

    @Test
    fun `setPhoneNumber does not update UserInfo when called with the empty phone number`() {
        val initialPhone = "+12223334444"
        UserInfo.phoneNumber = initialPhone

        Klaviyo.setPhoneNumber("")

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `setPhoneNumber does not update UserInfo when called with the same phone number`() {
        val initialPhone = "+12223334444"
        UserInfo.phoneNumber = initialPhone

        Klaviyo.setPhoneNumber(initialPhone)

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Sets an arbitrary user property`() {
        val stubName = "Gonzo"
        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, stubName)

        verifyProfileDebounced()
        assert(capturedProfile.isCaptured)
        assertEquals(stubName, capturedProfile.captured[ProfileKey.FIRST_NAME])
    }

    @Test
    fun `Resets user info`() {
        val anonId = UserInfo.anonymousId
        UserInfo.email = EMAIL
        UserInfo.phoneNumber = PHONE
        UserInfo.externalId = EXTERNAL_ID

        Klaviyo.resetProfile()

        assertNotEquals(anonId, UserInfo.anonymousId)
        assertEquals("", UserInfo.email)
        assertEquals("", UserInfo.phoneNumber)
        assertEquals("", UserInfo.externalId)

        // Shouldn't make an API request by default
        verify(inverse = true) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Reset removes push token from store`() {
        UserInfo.email = EMAIL
        dataStoreSpy.store("push_token", PUSH_TOKEN)

        Klaviyo.resetProfile()

        assertEquals("", UserInfo.email)
        assertEquals(null, dataStoreSpy.fetch("push_token"))
    }

    @Test
    fun `Gets identifiers out of user info`() {
        assertNull(Klaviyo.getEmail())
        assertNull(Klaviyo.getPhoneNumber())
        assertNull(Klaviyo.getExternalId())

        UserInfo.email = EMAIL
        UserInfo.phoneNumber = PHONE
        UserInfo.externalId = EXTERNAL_ID

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
