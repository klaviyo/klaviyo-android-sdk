package com.klaviyo.coresdk.model

import com.klaviyo.coresdk.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class UserInfoTest : BaseTest() {
    @Before
    override fun setup() {
        super.setup()
        UserInfo.reset()
    }

    @Test
    fun `UserInfo is convertible to Profile`() {
        UserInfo.externalId = EXTERNAL_ID
        UserInfo.email = EMAIL
        UserInfo.phoneNumber = PHONE
        assertProfileIdentifiers(UserInfo.getAsProfile())
        assertUserInfoIdentifiers()
    }

    @Test
    fun `Updates UserInfo identifiers from a Profile object`() {
        UserInfo.email = EMAIL

        val profile = Profile().setExternalId(EXTERNAL_ID).setPhoneNumber(PHONE)

        UserInfo.updateFromProfile(profile)

        assertUserInfoIdentifiers()
    }

    @Test
    fun `create and store a new UUID if one does not exists in data store`() {
        val anonId = UserInfo.anonymousId
        val fetched = dataStoreSpy.fetch(ProfileKey.ANONYMOUS.name)
        assertEquals(anonId, fetched)
    }

    @Test
    fun `do not create new UUID if one exists in data store`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS.name, ANON_ID)
        assertEquals(ANON_ID, UserInfo.anonymousId)
    }

    @Test
    fun `only read properties from data store once`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS.name, ANON_ID)
        dataStoreSpy.store(ProfileKey.EMAIL.name, EMAIL)
        dataStoreSpy.store(ProfileKey.EXTERNAL_ID.name, EXTERNAL_ID)
        dataStoreSpy.store(ProfileKey.PHONE_NUMBER.name, PHONE)

        var unusedRead = UserInfo.anonymousId
        assertEquals(UserInfo.anonymousId, ANON_ID)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.ANONYMOUS.name) }

        unusedRead = UserInfo.email
        assertEquals(UserInfo.email, EMAIL)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EMAIL.name) }

        unusedRead = UserInfo.externalId
        assertEquals(UserInfo.externalId, EXTERNAL_ID)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EXTERNAL_ID.name) }

        unusedRead = UserInfo.phoneNumber
        assertEquals(UserInfo.phoneNumber, PHONE)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.PHONE_NUMBER.name) }
    }

    @Test
    fun `Anonymous ID lifecycle`() {
        // Should be null after a reset...
        val initialAnonId = dataStoreSpy.fetch(ProfileKey.ANONYMOUS.name)
        assertNull(initialAnonId)

        // Start tracking a new anon ID and it should be persisted
        val firstAnonId = UserInfo.anonymousId
        assertEquals(firstAnonId, dataStoreSpy.fetch(ProfileKey.ANONYMOUS.name))

        // Reset again should nullify in data store
        UserInfo.reset()
        assertNull(dataStoreSpy.fetch(ProfileKey.ANONYMOUS.name))

        // Start tracking again should generate another new anon ID
        val newAnonId = UserInfo.anonymousId
        assertNotEquals(firstAnonId, newAnonId)
        assertEquals(newAnonId, dataStoreSpy.fetch(ProfileKey.ANONYMOUS.name))
    }

    private fun assertProfileIdentifiers(profile: Profile) {
        assert(profile.externalId == EXTERNAL_ID)
        assert(profile.email == EMAIL)
        assert(profile.phoneNumber == PHONE)
        assert(profile.anonymousId == UserInfo.anonymousId)
        assert(profile.toMap().count() == 4) // shouldn't contain any extras
    }

    private fun assertUserInfoIdentifiers() {
        assert(UserInfo.externalId == EXTERNAL_ID)
        assert(UserInfo.email == EMAIL)
        assert(UserInfo.phoneNumber == PHONE)
    }
}
