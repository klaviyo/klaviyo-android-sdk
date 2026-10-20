package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core.identity.IdentityChange
import com.klaviyo.core.identity.IdentityTransition
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class IdentityMonitorTest : BaseTest() {

    private lateinit var state: KlaviyoState
    private lateinit var sideEffects: StateSideEffects
    private val changes = mutableListOf<IdentityChange>()

    private val apiClientMock = mockk<ApiClient>().apply {
        every { onApiRequest(any(), any()) } returns Unit
        every { offApiRequest(any()) } returns Unit
        every { enqueueProfile(any()) } returns mockk(relaxed = true)
        every { enqueuePushToken(any(), any()) } returns mockk(relaxed = true)
        every { enqueueUnregisterPushToken(any(), any(), any()) } returns mockk(relaxed = true)
    }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<ApiClient>(apiClientMock)
        state = KlaviyoState()
        state.apiKey = API_KEY
        sideEffects = StateSideEffects(state, apiClientMock)
        sideEffects.identityMonitor.onIdentityChange { changes += it }
    }

    @After
    override fun cleanup() {
        sideEffects.detach()
        state.reset()
        Registry.unregister<ApiClient>()
        super.cleanup()
    }

    private fun identify(profile: Profile = Profile(email = EMAIL)) {
        state.setProfile(profile)
        changes.clear()
    }

    private fun assertProfileChange() {
        assertEquals(1, changes.size)
        assertEquals(IdentityTransition.PROFILE_CHANGE, changes.single().transition)
        assertEquals(API_KEY, changes.single().companyId)
    }

    @Test
    fun `company ID reads from state`() {
        assertEquals(API_KEY, sideEffects.identityMonitor.companyId)
    }

    @Test
    fun `resetting an identified profile is a profile change`() {
        identify()

        state.reset()

        assertProfileChange()
    }

    @Test
    fun `resetting an anonymous profile is a profile change`() {
        state.reset()

        assertProfileChange()
    }

    @Test
    fun `setProfile with a different email is a profile change`() {
        identify()

        state.setProfile(Profile(email = "other@domain.com"))

        assertProfileChange()
    }

    @Test
    fun `setProfile that shares no identifier is a profile change`() {
        identify()

        state.setProfile(Profile(phoneNumber = PHONE))

        assertProfileChange()
    }

    @Test
    fun `setProfile that conflicts on one identifier is a profile change`() {
        identify(Profile(email = EMAIL, phoneNumber = PHONE))

        state.setProfile(Profile(email = EMAIL, phoneNumber = "+15550000000"))

        assertProfileChange()
    }

    @Test
    fun `setProfile adding an identifier is not reported`() {
        identify()

        state.setProfile(Profile(email = EMAIL, phoneNumber = PHONE))

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `setProfile dropping an identifier is not reported`() {
        identify(Profile(email = EMAIL, phoneNumber = PHONE))

        state.setProfile(Profile(email = EMAIL))

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `setProfile with an email that differs only by case is not reported`() {
        identify()

        state.setProfile(Profile(email = EMAIL.uppercase()))

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `setProfile with the same identifiers and new attributes is not reported`() {
        identify()

        state.setProfile(Profile(email = EMAIL).setProperty("tier", "gold"))

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `setProfile with attributes and no identifiers on an identified profile is a profile change`() {
        identify()

        state.setProfile(Profile().setProperty("tier", "gold"))

        assertProfileChange()
        assertEquals(null, state.email)
    }

    @Test
    fun `setProfile on an anonymous profile is not reported`() {
        state.setProfile(Profile(email = EMAIL, phoneNumber = PHONE, externalId = EXTERNAL_ID))

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `overwriting the email with a different one is a profile change`() {
        identify()

        state.email = "other@domain.com"

        assertProfileChange()
    }

    @Test
    fun `overwriting the phone number with a different one is a profile change`() {
        identify(Profile(phoneNumber = PHONE))

        state.phoneNumber = "+15550000000"

        assertProfileChange()
    }

    @Test
    fun `overwriting the external ID with a different one is a profile change`() {
        identify(Profile(externalId = EXTERNAL_ID))

        state.externalId = "other-external-id"

        assertProfileChange()
    }

    @Test
    fun `overwriting the email with one that differs only by case is not reported`() {
        identify()

        state.email = EMAIL.uppercase()

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `overwriting the external ID with one that differs only by case is a profile change`() {
        identify(Profile(externalId = EXTERNAL_ID))

        state.externalId = EXTERNAL_ID.uppercase()

        assertProfileChange()
    }

    @Test
    fun `setting an identifier with no value is not reported`() {
        state.email = EMAIL
        state.phoneNumber = PHONE
        state.externalId = EXTERNAL_ID

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `setting an identifier on an identified profile that had none for it is not reported`() {
        identify()

        state.phoneNumber = PHONE
        state.externalId = EXTERNAL_ID

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `setting a profile attribute is not reported`() {
        identify()

        state.setAttribute(ProfileKey.CUSTOM("tier"), "gold")

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `changing the company is a company change`() {
        identify()

        state.apiKey = "newApiKey"

        assertEquals(1, changes.size)
        assertEquals(IdentityTransition.COMPANY_CHANGE, changes.single().transition)
        assertEquals("newApiKey", changes.single().companyId)
    }

    @Test
    fun `setting the first company is not reported`() {
        val freshState = KlaviyoState()
        freshState.reset()
        Registry.dataStore.clear("api_key")
        val freshEffects = StateSideEffects(freshState, apiClientMock)
        val seen = mutableListOf<IdentityChange>()
        freshEffects.identityMonitor.onIdentityChange { seen += it }

        freshState.apiKey = API_KEY

        assertTrue(seen.isEmpty())
        freshEffects.detach()
    }

    @Test
    fun `observer failure is logged and does not stop other observers`() {
        var reached = false
        sideEffects.identityMonitor.onIdentityChange { throw IllegalStateException("boom") }
        sideEffects.identityMonitor.onIdentityChange { reached = true }

        state.reset()

        assertTrue(reached)
        verify { spyLog.error(any(), any()) }
    }

    @Test
    fun `removed observer is not notified`() {
        val seen = mutableListOf<IdentityChange>()
        val observer: (IdentityChange) -> Unit = { seen += it }
        sideEffects.identityMonitor.onIdentityChange(observer)
        sideEffects.identityMonitor.offIdentityChange(observer)

        state.reset()

        assertTrue(seen.isEmpty())
    }
}
