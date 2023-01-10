package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.model.Profile
import org.junit.Test

class UserInfoTest {
    private val extId = "abc123"
    private val email = "test@email.com"
    private val phoneNumber = "802-233-2407"

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
        UserInfo.externalId = ""
        UserInfo.email = email
        UserInfo.phoneNumber = ""

        val profile = Profile()
            .setIdentifier(extId)
            .setPhoneNumber(phoneNumber)

        UserInfo.mergeProfile(profile)

        assertProfileIdentifiers(profile)
        assertUserInfoIdentifiers()
    }

    private fun assertProfileIdentifiers(profile: Profile) {
        assert(profile.identifier == extId)
        assert(profile.email == email)
        assert(profile.phoneNumber == phoneNumber)
        assert(profile.toMap().count() == 3) // asserts nothing extraneous was added
    }

    private fun assertUserInfoIdentifiers() {
        assert(UserInfo.externalId == extId)
        assert(UserInfo.email == email)
        assert(UserInfo.phoneNumber == phoneNumber)
    }
}
