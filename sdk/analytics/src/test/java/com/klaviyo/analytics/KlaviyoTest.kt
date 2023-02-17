package com.klaviyo.analytics

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
import org.junit.Assert.assertNull
import org.junit.Test

internal class KlaviyoTest : BaseTest() {

    private val capturedProfile = slot<Profile>()
    private val staticClock = StaticClock(TIME, ISO_TIME)
    private val debounceTime = 5
    private val apiClientMock: ApiClient = mockk()

    override fun setup() {
        super.setup()
        Registry.add<ApiClient> { apiClientMock }
        every { Registry.clock } returns staticClock
        every { apiClientMock.enqueueProfile(capture(capturedProfile)) } returns Unit
        every { apiClientMock.enqueueEvent(any(), any()) } returns Unit
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

    private fun verifyProfileDebounced() {
        staticClock.execute(debounceTime.toLong())
        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Profile updates are debounced`() {
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
            .setProfile(
                Profile().also {
                    it[stubMiddleNameKey] = stubMiddleName
                }
            )

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
        UserInfo.email = EMAIL
        UserInfo.phoneNumber = PHONE
        UserInfo.externalId = EXTERNAL_ID

        Klaviyo.resetProfile()

        assert(UserInfo.email == "")
        assert(UserInfo.phoneNumber == "")
        assert(UserInfo.externalId == "")

        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
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
    fun `Enqueue an event API call`() {
        val stubEvent = Event(EventType.VIEWED_PRODUCT).also { it[EventKey.VALUE] = 1 }
        Klaviyo.createEvent(stubEvent)

        verify(exactly = 1) {
            apiClientMock.enqueueEvent(stubEvent, any())
        }
    }
}
