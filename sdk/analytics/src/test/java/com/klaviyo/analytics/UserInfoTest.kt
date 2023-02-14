package com.klaviyo.analytics

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.core_shared_tests.BaseTest
import io.mockk.verify
import org.junit.Assert
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
        dataStoreSpy.store(ProfileKey.ANONYMOUS.name, "")
        val anonId = UserInfo.anonymousId
        val fetched = dataStoreSpy.fetch(ProfileKey.ANONYMOUS.name)
        Assert.assertEquals(anonId, fetched)
    }

    @Test
    fun `do not create new UUID if one exists in data store`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS.name, ANON_ID)
        Assert.assertEquals(ANON_ID, UserInfo.anonymousId)
    }

    @Test
    fun `only read properties from data store once`() {
        dataStoreSpy.store(ProfileKey.ANONYMOUS.name, ANON_ID)
        dataStoreSpy.store(ProfileKey.EMAIL.name, EMAIL)
        dataStoreSpy.store(ProfileKey.EXTERNAL_ID.name, EXTERNAL_ID)
        dataStoreSpy.store(ProfileKey.PHONE_NUMBER.name, PHONE)

        var unusedRead = UserInfo.anonymousId
        Assert.assertEquals(UserInfo.anonymousId, ANON_ID)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.ANONYMOUS.name) }

        unusedRead = UserInfo.email
        Assert.assertEquals(UserInfo.email, EMAIL)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EMAIL.name) }

        unusedRead = UserInfo.externalId
        Assert.assertEquals(UserInfo.externalId, EXTERNAL_ID)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.EXTERNAL_ID.name) }

        unusedRead = UserInfo.phoneNumber
        Assert.assertEquals(UserInfo.phoneNumber, PHONE)
        verify(exactly = 1) { dataStoreSpy.fetch(ProfileKey.PHONE_NUMBER.name) }
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
