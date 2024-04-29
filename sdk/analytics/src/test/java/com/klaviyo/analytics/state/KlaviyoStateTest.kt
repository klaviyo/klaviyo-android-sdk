package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class KlaviyoStateTest : BaseTest() {

    private lateinit var state: KlaviyoState

    @Before
    override fun setup() {
        super.setup()
        state = KlaviyoState().apply { reset() }
    }

    @Test
    fun `UserInfo is convertible to Profile`() {
        state.externalId = EXTERNAL_ID
        state.email = EMAIL
        state.phoneNumber = PHONE
        assertProfileIdentifiers(state.getAsProfile())
        assertUserInfoIdentifiers()
    }

    @Test
    fun `Create and store a new UUID if one does not exists in data store`() {
        val anonId = state.anonymousId
        val fetched = dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name)
        assertEquals(anonId, fetched)
    }

    @Test
    fun `Do not create new UUID if one exists in data store`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS_ID.name, ANON_ID)
        assertEquals(ANON_ID, state.anonymousId)
    }

    @Test
    fun `Only read properties from data store once`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS_ID.name, ANON_ID)
        dataStoreSpy.store(ProfileKey.EMAIL.name, EMAIL)
        dataStoreSpy.store(ProfileKey.EXTERNAL_ID.name, EXTERNAL_ID)
        dataStoreSpy.store(ProfileKey.PHONE_NUMBER.name, PHONE)

        state.anonymousId
        assertEquals(ANON_ID, state.anonymousId)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name) }

        state.email
        assertEquals(EMAIL, state.email)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EMAIL.name) }

        state.externalId
        assertEquals(EXTERNAL_ID, state.externalId)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EXTERNAL_ID.name) }

        state.phoneNumber
        assertEquals(PHONE, state.phoneNumber)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.PHONE_NUMBER.name) }
    }

    @Test
    fun `Anonymous ID lifecycle`() {
        // Should be null after a reset...
        val initialAnonId = dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name)
        assertNull(initialAnonId)

        // Start tracking a new anon ID and it should be persisted
        val firstAnonId = state.anonymousId
        assertEquals(firstAnonId, dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))

        // Reset again should nullify in data store
        state.reset()
        assertNull(dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))

        // Start tracking again should generate another new anon ID
        val newAnonId = state.anonymousId
        assertNotEquals(firstAnonId, newAnonId)
        assertEquals(newAnonId, dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))
    }

    private fun assertProfileIdentifiers(profile: Profile) {
        assert(profile.externalId == EXTERNAL_ID)
        assert(profile.email == EMAIL)
        assert(profile.phoneNumber == PHONE)
        assert(profile.anonymousId == state.anonymousId)
        assert(profile.toMap().count() == 4) // shouldn't contain any extras
    }

    private fun assertUserInfoIdentifiers() {
        assertEquals(EXTERNAL_ID, state.externalId)
        assertEquals(EMAIL, state.email)
        assertEquals(PHONE, state.phoneNumber)
    }
}
