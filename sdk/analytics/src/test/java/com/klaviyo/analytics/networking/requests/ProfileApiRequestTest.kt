package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.ProfileKey
import org.json.JSONObject
import org.junit.Test

internal class ProfileApiRequestTest : BaseApiRequestTest<ProfileApiRequest>() {

    override val expectedUrl = "client/profiles/"

    override fun makeTestRequest(): ProfileApiRequest =
        ProfileApiRequest(stubProfile)

    @Test
    fun `JSON interoperability`() = testJsonInterop(makeTestRequest())

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
