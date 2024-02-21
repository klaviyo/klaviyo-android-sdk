package com.klaviyo.analytics

import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.Log
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.LogFixture
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class KlaviyoUninitializedTest {
    companion object {
        private val logger = spyk(LogFixture()).apply {
            every { error(any(), any<Throwable>()) } answers {
                println(firstArg<String>())
                secondArg<Throwable>().printStackTrace()
            }
        }

        private val mockApiClient = mockk<ApiClient>()
    }

    @Before
    fun setup() {
        Registry.unregister<Config>()
        Registry.register<Log>(logger)
        Registry.register<ApiClient>(mockApiClient)
    }

    private inline fun <reified T> assertCaught() where T : Throwable {
        verify { logger.error(any(), any<T>()) }
    }

    @Test
    fun `Profile setter is protected`() {
        Klaviyo.setProfile(Profile())
        assertCaught<MissingConfig>()
    }

    @Test
    fun `Email setter is protected`() {
        Klaviyo.setEmail(BaseTest.EMAIL)
        assertCaught<MissingConfig>()
    }

    @Test
    fun `Email getter is protected`() {
        assertNull(Klaviyo.getEmail())
        assertCaught<MissingConfig>()
    }

    @Test
    fun `Phone setter is protected`() {
        Klaviyo.setPhoneNumber(BaseTest.PHONE)
        assertCaught<MissingConfig>()
    }

    @Test
    fun `Phone getter is protected`() {
        assertNull(Klaviyo.getPhoneNumber())
        assertCaught<MissingConfig>()
    }

    @Test
    fun `External ID setter is protected`() {
        Klaviyo.setExternalId(BaseTest.EXTERNAL_ID)
        assertCaught<MissingConfig>()
    }

    @Test
    fun `External ID getter is protected`() {
        assertNull(Klaviyo.getExternalId())
        assertCaught<MissingConfig>()
    }

    @Test
    fun `Push token setter is protected`() {
        Klaviyo.setPushToken(BaseTest.PUSH_TOKEN)
        assertCaught<MissingConfig>()
    }

    @Test
    fun `Push token getter is protected`() {
        assertNull(Klaviyo.getPushToken())
        assertCaught<MissingConfig>()
    }

    @Test
    fun `Profile Attributes setter is protected`() {
        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, "John")
        assertCaught<MissingConfig>()
    }

    @Test
    fun `ResetProfile is protected`() {
        Klaviyo.resetProfile()
        assertCaught<MissingConfig>()
    }

    @Test
    fun `CreateEvent is protected`() {
        Klaviyo.createEvent(EventMetric.VIEWED_PRODUCT, 1.0)
        assertCaught<MissingConfig>()
    }

    @Test
    fun `HandlePushToken is protected`() {
        Klaviyo.handlePush(KlaviyoTest.mockIntent(KlaviyoTest.stubIntentExtras))
        assertCaught<MissingConfig>()
    }
}
