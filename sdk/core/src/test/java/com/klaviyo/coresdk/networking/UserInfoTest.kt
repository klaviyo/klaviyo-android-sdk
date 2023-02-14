package com.klaviyo.coresdk.networking

import org.junit.Test

class UserInfoTest {
    private val extId = "abc123"
    private val email = "test@email.com"
    private val phoneNumber = "802-233-2407"

    @Test
    fun `UserInfo is convertible to KlaviyoCustomerProperties`() {
        UserInfo.externalId = extId
        UserInfo.email = email
        UserInfo.phoneNumber = phoneNumber
        assertProfileIdentifiers(UserInfo.getAsCustomerProperties())
        assertUserInfoIdentifiers()
    }

    @Test
    fun `Two way merge of KlaviyoCustomerProperties and UserInfo`() {
        UserInfo.externalId = ""
        UserInfo.email = email
        UserInfo.phoneNumber = ""

        val profile = KlaviyoCustomerProperties()
            .setIdentifier(extId)
            .setPhoneNumber(phoneNumber)

        UserInfo.mergeCustomerProperties(profile)

        assertProfileIdentifiers(profile)
        assertUserInfoIdentifiers()
    }

    private fun assertProfileIdentifiers(profile: KlaviyoCustomerProperties) {
        assert(profile.getIdentifier() == extId)
        assert(profile.getEmail() == email)
        assert(profile.getPhoneNumber() == phoneNumber)
        assert(profile.toMap().count() == 3) // asserts nothing extraneous was added
    }

    private fun assertUserInfoIdentifiers() {
        assert(UserInfo.externalId == extId)
        assert(UserInfo.email == email)
        assert(UserInfo.phoneNumber == phoneNumber)
    }
}
