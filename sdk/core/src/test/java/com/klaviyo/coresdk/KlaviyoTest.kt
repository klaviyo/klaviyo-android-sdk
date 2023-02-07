package com.klaviyo.coresdk

import com.klaviyo.coresdk.config.Config
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventAttributeKey
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.model.UserInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyAll
import org.junit.Test

internal class KlaviyoTest : BaseTest() {

    private val capturedProfile = slot<Profile>()

    override fun setup() {
        super.setup()
        every { apiClientMock.enqueueProfile(capture(capturedProfile)) } returns Unit
        every { apiClientMock.enqueueEvent(any(), any(), any()) } returns Unit
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
    fun `Sets user external ID into info`() {
        Klaviyo.setExternalId(EXTERNAL_ID)

        assert(UserInfo.externalId == EXTERNAL_ID)
        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Sets user email into info`() {
        Klaviyo.setEmail(EMAIL)

        assert(UserInfo.email == EMAIL)
        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Sets user phone into info`() {
        Klaviyo.setPhoneNumber(PHONE)

        assert(UserInfo.phoneNumber == PHONE)
        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Sets an arbitrary user property`() {
        val stubName = "Evan"
        Klaviyo.setProfileAttribute(KlaviyoProfileAttributeKey.FIRST_NAME, stubName)

        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
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

        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Enqueue an event API call`() {
        Klaviyo.createEvent(
            KlaviyoEventType.VIEWED_PRODUCT,
            Event().apply { this[KlaviyoEventAttributeKey.VALUE] = 1 }
        )

        verify(exactly = 1) {
            apiClientMock.enqueueEvent(
                KlaviyoEventType.VIEWED_PRODUCT,
                any(),
                any()
            )
        }
    }
}
