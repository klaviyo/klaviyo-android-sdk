package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core.identity.IdentityChange
import com.klaviyo.core.identity.IdentityTransition
import com.klaviyo.core.identity.ProfileIdentity
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    private fun assertSingleChange(
        transition: IdentityTransition,
        previous: ProfileIdentity,
        current: ProfileIdentity
    ) {
        assertEquals(1, changes.size)
        assertEquals(transition, changes.single().transition)
        assertEquals(previous, changes.single().previous)
        assertEquals(current, changes.single().current)
    }

    @Test
    fun `current identity reads company and anonymous ID from state`() {
        assertEquals(
            ProfileIdentity(API_KEY, state.anonymousId),
            sideEffects.identityMonitor.current
        )
    }

    @Test
    fun `resetting an identified profile is a replacement with a new anonymous ID`() {
        identify()
        val before = sideEffects.identityMonitor.current

        state.reset()

        val after = sideEffects.identityMonitor.current
        assertNotEquals(before.anonymousId, after.anonymousId)
        assertSingleChange(IdentityTransition.REPLACEMENT, before, after)
    }

    @Test
    fun `resetting an anonymous profile is a replacement`() {
        val before = sideEffects.identityMonitor.current

        state.reset()

        assertSingleChange(
            IdentityTransition.REPLACEMENT,
            before,
            sideEffects.identityMonitor.current
        )
    }

    @Test
    fun `setProfile with no matching identifier is a replacement`() {
        identify(Profile(email = EMAIL))
        val before = sideEffects.identityMonitor.current

        state.setProfile(Profile(email = "other@domain.com"))

        assertSingleChange(
            IdentityTransition.REPLACEMENT,
            before,
            sideEffects.identityMonitor.current
        )
    }

    @Test
    fun `setProfile with the same identifiers does not change identity`() {
        identify(Profile(email = EMAIL))

        state.setProfile(Profile(email = EMAIL).setProperty("tier", "gold"))

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `adding an identifier through setProfile rotates the anonymous ID and is compatible`() {
        identify(Profile(email = EMAIL))
        val before = sideEffects.identityMonitor.current

        state.setProfile(Profile(email = EMAIL, phoneNumber = PHONE))

        val after = sideEffects.identityMonitor.current
        assertNotEquals(before.anonymousId, after.anonymousId)
        assertSingleChange(IdentityTransition.COMPATIBLE, before, after)
    }

    @Test
    fun `setters on an anonymous profile keep the anonymous ID and report nothing`() {
        val before = sideEffects.identityMonitor.current

        state.email = EMAIL
        state.phoneNumber = PHONE
        state.externalId = EXTERNAL_ID

        assertTrue(changes.isEmpty())
        assertEquals(before, sideEffects.identityMonitor.current)
    }

    @Test
    fun `changing the company keeps the anonymous ID and is a company change`() {
        identify()
        val anonymousId = state.anonymousId

        state.apiKey = "newApiKey"

        assertSingleChange(
            IdentityTransition.COMPANY_CHANGE,
            ProfileIdentity(API_KEY, anonymousId),
            ProfileIdentity("newApiKey", anonymousId)
        )
    }

    @Test
    fun `setting the first company is not a company change`() {
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
