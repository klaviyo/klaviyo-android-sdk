package com.klaviyo.coresdk.networking

// TODO: Eventually we want to build this up into a user session
// but for now we just need emails on initialization to associate push tokens with accounts
/**
 * Stores information on the currently active user
 */
internal object UserInfo {
    var externalId: String = ""
    var email: String = ""
    var phoneNumber: String = ""
    // TODO should anon ID be here with all the other identifiers

    fun hasExternalId(): Boolean {
        return externalId.isNotEmpty()
    }

    fun hasEmail(): Boolean {
        return email.isNotEmpty()
    }

    fun hasPhoneNumber(): Boolean {
        return phoneNumber.isNotEmpty()
    }

    fun reset() {
        externalId = ""
        email = ""
        phoneNumber = ""
    }

    /**
     * Construct a [KlaviyoCustomerProperties] object containing current UserInfo identifiers
     *
     * @return
     */
    fun getAsCustomerProperties(): KlaviyoCustomerProperties {
        return KlaviyoCustomerProperties().also {
            setCustomerProperties(it)
        }
    }

    /**
     * Apply all identifiers from UserInfo to a [KlaviyoCustomerProperties] object
     *
     * @param properties
     */
    private fun setCustomerProperties(properties: KlaviyoCustomerProperties) {
        if (hasExternalId()) {
            properties.setIdentifier(externalId)
        }

        if (hasEmail()) {
            properties.setEmail(email)
        }

        if (hasPhoneNumber()) {
            properties.setPhoneNumber(phoneNumber)
        }
    }

    /**
     * Two-way merge of a [KlaviyoCustomerProperties] object with UserInfo
     * Identifiers present on the incoming object will be applied to UserInfo
     * Any other identifiers will be added to the properties object from UserInfo
     *
     * @param properties
     */
    fun mergeCustomerProperties(properties: KlaviyoCustomerProperties): KlaviyoCustomerProperties {
        externalId = properties.getIdentifier() ?: externalId
        email = properties.getEmail() ?: email
        phoneNumber = properties.getPhoneNumber() ?: phoneNumber
        setCustomerProperties(properties)
        return properties
    }
}
