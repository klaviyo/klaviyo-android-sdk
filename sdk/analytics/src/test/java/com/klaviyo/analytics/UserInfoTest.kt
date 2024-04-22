package com.klaviyo.analytics

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.state.UserInfo
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class UserInfoTest : BaseTest() {

    private lateinit var userInfo: UserInfo

    @Before
    override fun setup() {
        super.setup()
        userInfo = UserInfo().apply { reset() }
    }

    @Test
    fun `UserInfo is convertible to Profile`() {
        userInfo.externalId = EXTERNAL_ID
        userInfo.email = EMAIL
        userInfo.phoneNumber = PHONE
        assertProfileIdentifiers(userInfo.get())
        assertUserInfoIdentifiers()
    }

    @Test
    fun `Create and store a new UUID if one does not exists in data store`() {
        val anonId = userInfo.anonymousId
        val fetched = dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name)
        assertEquals(anonId, fetched)
    }

    @Test
    fun `Do not create new UUID if one exists in data store`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS_ID.name, ANON_ID)
        assertEquals(ANON_ID, userInfo.anonymousId)
    }

    @Test
    fun `Only read properties from data store once`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS_ID.name, ANON_ID)
        dataStoreSpy.store(ProfileKey.EMAIL.name, EMAIL)
        dataStoreSpy.store(ProfileKey.EXTERNAL_ID.name, EXTERNAL_ID)
        dataStoreSpy.store(ProfileKey.PHONE_NUMBER.name, PHONE)

        userInfo.anonymousId
        assertEquals(ANON_ID, userInfo.anonymousId)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name) }

        userInfo.email
        assertEquals(EMAIL, userInfo.email)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EMAIL.name) }

        userInfo.externalId
        assertEquals(EXTERNAL_ID, userInfo.externalId)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EXTERNAL_ID.name) }

        userInfo.phoneNumber
        assertEquals(PHONE, userInfo.phoneNumber)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.PHONE_NUMBER.name) }
    }

    @Test
    fun `Anonymous ID lifecycle`() {
        // Should be null after a reset...
        val initialAnonId = dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name)
        assertNull(initialAnonId)

        // Start tracking a new anon ID and it should be persisted
        val firstAnonId = userInfo.anonymousId
        assertEquals(firstAnonId, dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))

        // Reset again should nullify in data store
        userInfo.reset()
        assertNull(dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))

        // Start tracking again should generate another new anon ID
        val newAnonId = userInfo.anonymousId
        assertNotEquals(firstAnonId, newAnonId)
        assertEquals(newAnonId, dataStoreSpy.fetch(ProfileKey.ANONYMOUS_ID.name))
    }

    private fun assertProfileIdentifiers(profile: Profile) {
        assert(profile.externalId == EXTERNAL_ID)
        assert(profile.email == EMAIL)
        assert(profile.phoneNumber == PHONE)
        assert(profile.anonymousId == userInfo.anonymousId)
        assert(profile.toMap().count() == 4) // shouldn't contain any extras
    }

    private fun assertUserInfoIdentifiers() {
        assertEquals(EXTERNAL_ID, userInfo.externalId)
        assertEquals(EMAIL, userInfo.email)
        assertEquals(PHONE, userInfo.phoneNumber)
    }
}
