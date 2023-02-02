package com.klaviyo.coresdk

import com.klaviyo.coresdk.helpers.BaseTest
import com.klaviyo.coresdk.helpers.InMemoryDataStore
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventAttributeKey
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.model.UserInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test

class KlaviyoTest : BaseTest() {

    private val capturedProfile = slot<Profile>()

    override fun setup() {
        super.setup()
        mockkObject(Klaviyo.Registry)
        every { Klaviyo.Registry.networkMonitor } returns mockk()
        every { Klaviyo.Registry.dataStore } returns spyk(InMemoryDataStore)
        every { Klaviyo.Registry.apiClient } returns mockk()
        every { Klaviyo.Registry.apiClient.enqueueProfile(capture(capturedProfile)) } returns Unit
        every { Klaviyo.Registry.apiClient.enqueueEvent(any(), any(), any()) } returns Unit

        Klaviyo.initialize(
            apiKey = API_KEY,
            applicationContext = contextMock
        )
    }

    @Test
    fun `Verify expected BuildConfig properties`() {
        // This is also just a test coverage boost
        assert(BuildConfig() is BuildConfig)
        assert(BuildConfig.DEBUG is Boolean)
        assert(BuildConfig.LIBRARY_PACKAGE_NAME == "com.klaviyo.coresdk")
        assert(BuildConfig.BUILD_TYPE is String)
        assert(BuildConfig.KLAVIYO_SERVER_URL is String)
    }

    @Test
    fun `Klaviyo Configure API sets variables successfully`() {
        assert(KlaviyoConfig.apiKey == API_KEY)
        assert(KlaviyoConfig.applicationContext == contextMock)
    }

    @Test
    fun `Sets user external ID into info`() {
        Klaviyo.setExternalId(EXTERNAL_ID)

        assert(UserInfo.externalId == EXTERNAL_ID)
        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Sets user email into info`() {
        Klaviyo.setEmail(EMAIL)

        assert(UserInfo.email == EMAIL)
        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Sets user phone into info`() {
        Klaviyo.setPhoneNumber(PHONE)

        assert(UserInfo.phoneNumber == PHONE)
        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Sets an arbitrary user property`() {
        val stubName = "Evan"
        Klaviyo.setProfileAttribute(KlaviyoProfileAttributeKey.FIRST_NAME, stubName)

        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
        assert(capturedProfile.isCaptured)
        assert(capturedProfile.captured[KlaviyoProfileAttributeKey.FIRST_NAME] == stubName)
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

        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Enqueue an event API call`() {
        Klaviyo.createEvent(
            KlaviyoEventType.VIEWED_PRODUCT,
            Event().apply { this[KlaviyoEventAttributeKey.VALUE] = 1 }
        )

        verify(exactly = 1) {
            Klaviyo.Registry.apiClient.enqueueEvent(
                KlaviyoEventType.VIEWED_PRODUCT,
                any(),
                any()
            )
        }
    }
}
