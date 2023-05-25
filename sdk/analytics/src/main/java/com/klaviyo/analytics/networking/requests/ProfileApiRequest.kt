package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.core.Registry
import java.io.Serializable

/**
 * Defines the content of an API request to identify [Profile] data
 *
 * Using V3 API
 *
 * @constructor
 */
internal class ProfileApiRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.POST, queuedTime, uuid) {

    private companion object {
        const val PATH = "client/profiles/"
        const val LOCATION = "location"
        const val META = "meta"
        const val IDENTIFIERS = "identifiers"
    }

    override var type: String = "Identify Profile"

    override var headers: Map<String, String> = mapOf(
        HEADER_CONTENT to TYPE_JSON,
        HEADER_ACCEPT to TYPE_JSON,
        HEADER_REVISION to V3_REVISION,
        HEADER_USER_AGENT to Registry.config.userAgent
    )

    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override val successCodes: IntRange get() = HTTP_ACCEPTED..HTTP_ACCEPTED

    constructor(profile: Profile) : this() {
        // Create a mutable copy of the profile
        // We'll pop off all the enumerated keys as we build the body
        // Then any remaining pairs are custom keys
        val properties = profile.toMap().toMutableMap()
        fun extract(key: ProfileKey): Pair<String, Serializable?> =
            key.name to properties.remove(key.name)

        body = jsonMapOf(
            DATA to mapOf(
                TYPE to PROFILE,
                ATTRIBUTES to filteredMapOf( // All of the enumerated keys are "attributes"
                    extract(ProfileKey.EMAIL),
                    extract(ProfileKey.PHONE_NUMBER),
                    extract(ProfileKey.EXTERNAL_ID),
                    extract(ProfileKey.ANONYMOUS_ID),
                    extract(ProfileKey.FIRST_NAME),
                    extract(ProfileKey.LAST_NAME),
                    extract(ProfileKey.ORGANIZATION),
                    extract(ProfileKey.TITLE),
                    extract(ProfileKey.IMAGE),

                    LOCATION to filteredMapOf(
                        extract(ProfileKey.ADDRESS1),
                        extract(ProfileKey.ADDRESS2),
                        extract(ProfileKey.CITY),
                        extract(ProfileKey.COUNTRY),
                        extract(ProfileKey.LATITUDE),
                        extract(ProfileKey.LONGITUDE),
                        extract(ProfileKey.REGION),
                        extract(ProfileKey.ZIP),
                        extract(ProfileKey.TIMEZONE)
                    ),

                    PROPERTIES to properties // Any remaining custom keys are properties
                ),
                META to mapOf(
                    // It is critical for JSON encoding that we convert all keys to strings
                    IDENTIFIERS to profile.getIdentifiers().mapKeys { it.key.name }
                )
            )
        )
    }
}
