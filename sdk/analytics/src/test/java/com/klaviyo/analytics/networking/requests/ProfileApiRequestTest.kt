package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.fixtures.BaseTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ProfileApiRequestTest : BaseTest() {

    private val expectedUrlPath = "client/profiles/"

    private val expectedQueryData = mapOf("company_id" to API_KEY)

    private val expectedHeaders = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Revision" to "2023-01-24"
    )

    private val stubProfile = Profile()
        .setEmail(EMAIL)
        .setPhoneNumber(PHONE)
        .setExternalId(EXTERNAL_ID)
        .setAnonymousId(ANON_ID)

    @Test
    fun `Uses correct endpoint`() {
        assertEquals(expectedUrlPath, ProfileApiRequest(stubProfile).urlPath)
    }

    @Test
    fun `Uses correct method`() {
        assertEquals(RequestMethod.POST, ProfileApiRequest(stubProfile).method)
    }

    @Test
    fun `Uses correct headers`() {
        assertEquals(expectedHeaders, ProfileApiRequest(stubProfile).headers)
    }

    @Test
    fun `Uses API Key in query`() {
        assertEquals(expectedQueryData, ProfileApiRequest(stubProfile).query)
    }

    @Test
    fun `JSON interoperability`() {
        val request = ProfileApiRequest(stubProfile)
        val requestJson = request.toJson()
        val revivedRequest = KlaviyoApiRequestDecoder.fromJson(requestJson)
        assert(revivedRequest is ProfileApiRequest)
        compareJson(requestJson, revivedRequest.toJson())
    }

    @Test
    fun `Formats body correctly`() {
        val expectJson = """{
            "data": {
                "type": "profile",
                "attributes": {
                  "email": "$EMAIL",
                  "phone_number": "$PHONE",
                  "external_id": "$EXTERNAL_ID",
                  "anonymous_id": "$ANON_ID",
                  "first_name": "Sarah",
                  "last_name": "Mason",
                  "organization": "Klaviyo",
                  "title": "Engineer",
                  "image": "image_url",
                  "location": {
                    "address1": "89 E 42nd St",
                    "address2": "1st floor",
                    "city": "New York",
                    "country": "United States",
                    "latitude": 123,
                    "longitude": 123,
                    "region": "NY",
                    "zip": "10017",
                    "timezone": "America/New_York"
                  },
                  "properties": {
                    "custom_key1": "custom_1",
                    "custom_key2": "custom_2"
                  }
                },
                "meta": {
                  "identifiers": {
                    "email": "$EMAIL",
                    "phone_number": "$PHONE",
                    "external_id": "$EXTERNAL_ID",
                    "anonymous_id": "$ANON_ID"
                  }
                }
            }
        }"""

        stubProfile
            .setProperty(ProfileKey.FIRST_NAME, "Sarah")
            .setProperty(ProfileKey.LAST_NAME, "Mason")
            .setProperty(ProfileKey.ORGANIZATION, "Klaviyo")
            .setProperty(ProfileKey.TITLE, "Engineer")
            .setProperty(ProfileKey.IMAGE, "image_url")
            .setProperty(ProfileKey.ADDRESS1, "89 E 42nd St")
            .setProperty(ProfileKey.ADDRESS2, "1st floor")
            .setProperty(ProfileKey.CITY, "New York")
            .setProperty(ProfileKey.COUNTRY, "United States")
            .setProperty(ProfileKey.LATITUDE, 123)
            .setProperty(ProfileKey.LONGITUDE, 123)
            .setProperty(ProfileKey.REGION, "NY")
            .setProperty(ProfileKey.ZIP, "10017")
            .setProperty(ProfileKey.TIMEZONE, "America/New_York")
            .setProperty("custom_key1", "custom_1")
            .setProperty("custom_key2", "custom_2")

        val request = ProfileApiRequest(stubProfile)

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }

    @Test
    fun `Body omits missing keys`() {
        val expectJson = """{
            "data": {
                "type": "profile",
                "attributes": {
                  "email": "$EMAIL",
                  "phone_number": "$PHONE",
                  "external_id": "$EXTERNAL_ID",
                  "anonymous_id": "$ANON_ID",
                  "first_name": "Sarah",
                  "last_name": "Mason"
                },
                "meta": {
                  "identifiers": {
                    "email": "$EMAIL",
                    "phone_number": "$PHONE",
                    "external_id": "$EXTERNAL_ID",
                    "anonymous_id": "$ANON_ID"
                  }
                }
            }
        }"""

        stubProfile
            .setProperty(ProfileKey.FIRST_NAME, "Sarah")
            .setProperty(ProfileKey.LAST_NAME, "Mason")

        val request = ProfileApiRequest(stubProfile)

        compareJson(JSONObject(expectJson), JSONObject(request.requestBody!!))
    }
}
