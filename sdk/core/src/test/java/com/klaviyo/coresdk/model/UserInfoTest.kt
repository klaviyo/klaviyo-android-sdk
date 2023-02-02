package com.klaviyo.coresdk.model

import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.helpers.BaseTest
import com.klaviyo.coresdk.helpers.InMemoryDataStore
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class UserInfoTest : BaseTest() {
    private val spyDataStore = spyk(InMemoryDataStore)

    @Before
    override fun setup() {
        super.setup()
        mockkObject(Klaviyo.Registry)
        every { Klaviyo.Registry.dataStore } returns spyDataStore
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
    fun `Two way merge of a Profile and UserInfo`() {
        UserInfo.email = EMAIL

        val profile = Profile().setIdentifier(EXTERNAL_ID).setPhoneNumber(PHONE)

        UserInfo.mergeProfile(profile)

        assertProfileIdentifiers(profile)
        assertUserInfoIdentifiers()
    }

    @Test
    fun `create and store a new UUID if one does not exists in data store`() {
        Klaviyo.Registry.dataStore.store(KlaviyoProfileAttributeKey.ANONYMOUS_ID.name, "")
        val anonId = UserInfo.anonymousId
        val fetched = Klaviyo.Registry.dataStore.fetch(KlaviyoProfileAttributeKey.ANONYMOUS_ID.name)
        Assert.assertEquals(anonId, fetched)
    }

    @Test
    fun `do not create new UUID if one exists in data store`() {
        Klaviyo.Registry.dataStore.store(KlaviyoProfileAttributeKey.ANONYMOUS_ID.name, ANON_ID)
        Assert.assertEquals(ANON_ID, UserInfo.anonymousId)
    }

    @Test
    fun `only read properties from data store once`() {
        Klaviyo.Registry.dataStore.store(KlaviyoProfileAttributeKey.ANONYMOUS_ID.name, ANON_ID)
        Klaviyo.Registry.dataStore.store(KlaviyoProfileAttributeKey.EMAIL.name, EMAIL)
        Klaviyo.Registry.dataStore.store(KlaviyoProfileAttributeKey.EXTERNAL_ID.name, EXTERNAL_ID)
        Klaviyo.Registry.dataStore.store(KlaviyoProfileAttributeKey.PHONE_NUMBER.name, PHONE)

        var unusedRead = UserInfo.anonymousId
        Assert.assertEquals(UserInfo.anonymousId, ANON_ID)
        verify(exactly = 1) { spyDataStore.fetch(KlaviyoProfileAttributeKey.ANONYMOUS_ID.name) }

        unusedRead = UserInfo.email
        Assert.assertEquals(UserInfo.email, EMAIL)
        verify(exactly = 1) { spyDataStore.fetch(KlaviyoProfileAttributeKey.EMAIL.name) }

        unusedRead = UserInfo.externalId
        Assert.assertEquals(UserInfo.externalId, EXTERNAL_ID)
        verify(exactly = 1) { spyDataStore.fetch(KlaviyoProfileAttributeKey.EXTERNAL_ID.name) }

        unusedRead = UserInfo.phoneNumber
        Assert.assertEquals(UserInfo.phoneNumber, PHONE)
        verify(exactly = 1) { spyDataStore.fetch(KlaviyoProfileAttributeKey.PHONE_NUMBER.name) }
    }

    private fun assertProfileIdentifiers(profile: Profile) {
        assert(profile.identifier == EXTERNAL_ID)
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
