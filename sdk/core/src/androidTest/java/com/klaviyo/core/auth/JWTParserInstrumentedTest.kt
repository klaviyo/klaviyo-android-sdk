package com.klaviyo.core.auth

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the device's [Base64] decoder, which unit tests replace with the strict JVM one.
 */
@RunWith(AndroidJUnit4::class)
class JWTParserInstrumentedTest {

    @Test
    fun base64DefaultSkipsNonAlphabetBytes() {
        assertArrayEquals(
            Base64.decode("abcd", Base64.DEFAULT),
            Base64.decode("ab!cd", Base64.DEFAULT)
        )
    }

    @Test
    fun parserRejectsPayloadThatBase64DefaultWouldDecode() {
        val header = encode(JSONObject(mapOf("alg" to "HS256")).toString())
        val payload = encode(
            JSONObject(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))
                .toString()
        )
        val junkPayload = payload.substring(0, 4) + "!" + payload.substring(4)

        assertArrayEquals(
            Base64.decode(payload, Base64.URL_SAFE),
            Base64.decode(junkPayload, Base64.URL_SAFE)
        )
        assertEquals(
            JWTValidationResult.MalformedBase64,
            JWTParser.parseAndValidate("$header.$junkPayload.sig", nowEpochSeconds = NOW_SECONDS)
        )
    }

    private fun encode(value: String): String =
        Base64.encodeToString(
            value.toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

    private companion object {
        const val NOW_SECONDS = 1_700_000_000L
        const val IAT_SECONDS = NOW_SECONDS - 60
        const val EXP_SECONDS = NOW_SECONDS + 3600
    }
}
