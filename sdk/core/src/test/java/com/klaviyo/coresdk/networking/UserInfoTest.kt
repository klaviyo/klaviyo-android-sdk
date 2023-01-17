package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.helpers.BaseTest
import com.klaviyo.coresdk.helpers.InMemoryDataStore
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey
import com.klaviyo.coresdk.model.Profile
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class UserInfoTest : BaseTest() {
    private val extId = "abc123"
    private val email = "test@email.com"
    private val phoneNumber = "802-233-2407"
    private val anonId = "anonId123"
    private val spyDataStore = spyk(InMemoryDataStore)

    @Before
    override fun setup() {
        mockkObject(Klaviyo.Registry)
        every { Klaviyo.Registry.dataStore } returns spyDataStore
        UserInfo.reset()
    }

    @Test
    fun `UserInfo is convertible to Profile`() {
        UserInfo.externalId = extId
        UserInfo.email = email
        UserInfo.phoneNumber = phoneNumber
        assertProfileIdentifiers(UserInfo.getAsProfile())
        assertUserInfoIdentifiers()
    }

    @Test
    fun `Two way merge of a Profile and UserInfo`() {
        UserInfo.email = email

        val profile = Profile().setIdentifier(extId).setPhoneNumber(phoneNumber)

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
        Klaviyo.Registry.dataStore.store(KlaviyoProfileAttributeKey.ANONYMOUS_ID.name, anonId)
        Assert.assertEquals(anonId, UserInfo.anonymousId)
    }

    @Test
    fun `only read UUID from data store once`() {
        Klaviyo.Registry.dataStore.store(KlaviyoProfileAttributeKey.ANONYMOUS_ID.name, anonId)
        UserInfo.anonymousId
        UserInfo.anonymousId
        UserInfo.anonymousId
        verify(exactly = 1) { spyDataStore.fetch(KlaviyoProfileAttributeKey.ANONYMOUS_ID.name) }
        Assert.assertEquals(anonId, UserInfo.anonymousId)
    }

    private fun assertProfileIdentifiers(profile: Profile) {
        assert(profile.identifier == extId)
        assert(profile.email == email)
        assert(profile.phoneNumber == phoneNumber)
        assert(profile.anonymousId == UserInfo.anonymousId)
        assert(profile.toMap().count() == 4) // shouldn't contain any extras
    }

    private fun assertUserInfoIdentifiers() {
        assert(UserInfo.externalId == extId)
        assert(UserInfo.email == email)
        assert(UserInfo.phoneNumber == phoneNumber)
    }
}
