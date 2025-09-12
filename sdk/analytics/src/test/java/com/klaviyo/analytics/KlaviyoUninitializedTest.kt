package com.klaviyo.analytics

import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.LogFixture
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class KlaviyoUninitializedTest {

    private val spyLog = spyk(LogFixture())

    @Before
    fun setup() {
        mockkObject(Registry)
        every { Registry.log } returns spyLog
    }

    @After
    fun cleanup() {
        unmockkObject(Registry)
    }

    private inline fun <reified T> assertLoggedError() where T : Throwable {
        verify { spyLog.error(any(), any<T>()) }
    }

    private inline fun <reified T> assertLoggedWarning() where T : Throwable {
        verify { spyLog.error(any(), any<T>()) }
    }

    @Test
    fun `Profile setter is protected`() {
        Klaviyo.setProfile(Profile())
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `Email setter is protected`() {
        Klaviyo.setEmail(BaseTest.EMAIL)
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `Email getter is protected`() {
        assertNull(Klaviyo.getEmail())
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `Phone setter is protected`() {
        Klaviyo.setPhoneNumber(BaseTest.PHONE)
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `Phone getter is protected`() {
        assertNull(Klaviyo.getPhoneNumber())
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `External ID setter is protected`() {
        Klaviyo.setExternalId(BaseTest.EXTERNAL_ID)
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `External ID getter is protected`() {
        assertNull(Klaviyo.getExternalId())
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `Push token setter is protected`() {
        Klaviyo.setPushToken(BaseTest.PUSH_TOKEN)
        assertLoggedWarning<MissingConfig>()
    }

    @Test
    fun `Push token getter is protected`() {
        Klaviyo.getPushToken()
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `Profile Attributes setter is protected`() {
        Klaviyo.setProfileAttribute(ProfileKey.FIRST_NAME, "John")
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `ResetProfile is protected`() {
        Klaviyo.resetProfile()
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `CreateEvent is protected`() {
        Klaviyo.createEvent(EventMetric.VIEWED_PRODUCT, 1.0)
        assertLoggedError<MissingConfig>()
    }

    @Test
    fun `HandlePushToken is protected`() {
        Klaviyo.handlePush(KlaviyoTest.mockIntent(KlaviyoTest.stubIntentExtras))
        assertLoggedWarning<MissingConfig>()
    }
}
