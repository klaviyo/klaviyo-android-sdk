package com.klaviyo.analytics

import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent
import android.os.Bundle
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core_shared_tests.BaseTest
import com.klaviyo.core_shared_tests.StaticClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class KlaviyoTest : BaseTest() {

    private val capturedProfile = slot<Profile>()
    private val staticClock = StaticClock(TIME, ISO_TIME)
    private val debounceTime = 5
    private val apiClientMock: ApiClient = mockk()

    override fun setup() {
        super.setup()
        UserInfo.reset()
        Registry.register<ApiClient> { apiClientMock }
        every { Registry.clock } returns staticClock
        every { apiClientMock.enqueueProfile(capture(capturedProfile)) } returns Unit
        every { apiClientMock.enqueueEvent(any(), any()) } returns Unit
        every { apiClientMock.enqueuePushToken(any(), any()) } returns Unit
        every { configMock.debounceInterval } returns debounceTime
    }

    @Test
    fun `Registered mock api`() {
        assertEquals(apiClientMock, Registry.get<ApiClient>())
    }

    @Test
    fun `Klaviyo initializes properly creates new config service`() {
        val builderMock = mockk<Config.Builder>()
        every { Registry.configBuilder } returns builderMock
        every { builderMock.apiKey(any()) } returns builderMock
        every { builderMock.applicationContext(any()) } returns builderMock
        every { builderMock.build() } returns configMock

        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = contextMock
        )

        verifyAll {
            builderMock.apiKey(API_KEY)
            builderMock.applicationContext(contextMock)
            builderMock.build()
        }
    }

    @Test
    fun `Klaviyo makes core lifecycle callbacks service available`() {
        val mockLifecycleCallbacks = mockk<ActivityLifecycleCallbacks>()
        every { Registry.lifecycleCallbacks } returns mockLifecycleCallbacks
        assertEquals(mockLifecycleCallbacks, Klaviyo.lifecycleCallbacks)
    }

    private fun verifyProfileDebounced() {
        staticClock.execute(debounceTime.toLong())
        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `setProfile is debounced`() {
        Klaviyo.setProfile(Profile().setEmail(EMAIL))

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
        verifyProfileDebounced()
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
    fun `setProfile resets current profile and passes new identifiers to UserInfo`() {
        UserInfo.email = "other"
        val anonId = UserInfo.anonymousId

        Klaviyo.setProfile(
            Profile()
                .setExternalId(EXTERNAL_ID)
        )

        assert(UserInfo.externalId == EXTERNAL_ID)
        assert(UserInfo.email == "")
        assertNotEquals(anonId, UserInfo.anonymousId)
    }

    @Test
    fun `Sets user external ID into info`() {
        Klaviyo.setExternalId(EXTERNAL_ID)

        assert(UserInfo.externalId == EXTERNAL_ID)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets user email into info`() {
        Klaviyo.setEmail(EMAIL)

        assert(UserInfo.email == EMAIL)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets user phone into info`() {
        Klaviyo.setPhoneNumber(PHONE)

        assert(UserInfo.phoneNumber == PHONE)
        verifyProfileDebounced()
    }

    @Test
    fun `Sets an arbitrary user property`() {
        val stubName = "Gonzo"
        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, stubName)

        verifyProfileDebounced()
        assert(capturedProfile.isCaptured)
        assert(capturedProfile.captured[ProfileKey.FIRST_NAME] == stubName)
    }

    @Test
    fun `Resets user info`() {
        val anonId = UserInfo.anonymousId
        UserInfo.email = EMAIL
        UserInfo.phoneNumber = PHONE
        UserInfo.externalId = EXTERNAL_ID

        Klaviyo.resetProfile()

        assertNotEquals(anonId, UserInfo.anonymousId)
        assert(UserInfo.email == "")
        assert(UserInfo.phoneNumber == "")
        assert(UserInfo.externalId == "")

        // Shouldn't make an API request by default
        verify(inverse = true) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Reset re-associates push token to new anonymous profile and removes from store`() {
        UserInfo.email = EMAIL
        dataStoreSpy.store("push_token", PUSH_TOKEN)

        Klaviyo.resetProfile()

        assert(UserInfo.email == "")
        assertEquals(null, dataStoreSpy.fetch("push_token"))
        verify(exactly = 1) { apiClientMock.enqueuePushToken(any(), any()) }
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

    private val stubPushPayload = mapOf(
        "body" to "Message body",
        "_k" to """{
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

    private fun mockIntent(payload: Map<String, String>): Intent {
        // Mocking an intent to return the stub push payload...
        val intent = mockk<Intent>()
        val bundle = mockk<Bundle>()
        var gettingKey = ""
        every { intent.extras } returns bundle
        every { bundle.keySet() } returns payload.keys
        every {
            bundle.getString(
                match { s ->
                    gettingKey = s // there must be a better way to do this...
                    payload.containsKey(s)
                },
                String()
            )
        } returns (payload[gettingKey] ?: "")

        return intent
    }

    @Test
    fun `Identifies push payload origin`() {
        // Handle push intent
        assertEquals(true, Klaviyo.isKlaviyoPush(stubPushPayload))
        assertEquals(false, Klaviyo.isKlaviyoPush(mapOf("other" to "3rd party push")))
    }

    @Test
    fun `Handling opened push Intent enqueues $opened_push API Call`() {
        // Handle push intent
        Klaviyo.handlePush(mockIntent(stubPushPayload))

        verify { apiClientMock.enqueueEvent(any(), any()) }
    }

    @Test
    fun `Non-klaviyo push payload is ignored`() {
        // doesn't have _k, klaviyo tracking params
        Klaviyo.handlePush(mockIntent(mapOf("other" to "3rd party push")))
        Klaviyo.handlePush(null)

        verify(inverse = true) { apiClientMock.enqueueEvent(any(), any()) }
    }

    @Test
    fun `Enqueue an event API call`() {
        val stubEvent = Event(EventType.VIEWED_PRODUCT).also { it[EventKey.VALUE] = 1 }
        Klaviyo.createEvent(stubEvent)

        verify(exactly = 1) {
            apiClientMock.enqueueEvent(stubEvent, any())
        }
    }
}
