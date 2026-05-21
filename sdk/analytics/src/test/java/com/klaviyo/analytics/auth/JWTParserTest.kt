package com.klaviyo.analytics.auth

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JWTParserTest : BaseTest() {
    companion object {
        private const val NOW_SECONDS = 1_700_000_000L
        private const val IAT_SECONDS = NOW_SECONDS - 60
        private const val EXP_SECONDS = NOW_SECONDS + 3600
    }

    // MARK: - Happy path

    @Test
    fun defaultLeewayIs30Seconds() {
        assertEquals(30L, JWTParser.DEFAULT_LEEWAY_SECONDS)
    }

    @Test
    fun validTokenReturnsValidWithDecodedClaims() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertTrue(result is JWTValidationResult.Valid)
        val valid = result as JWTValidationResult.Valid
        assertEquals(token, valid.token.rawToken)
        assertEquals(EXP_SECONDS, valid.token.expiresAtEpochSeconds)
        assertEquals(IAT_SECONDS, valid.token.issuedAtEpochSeconds)
    }

    @Test
    fun validTokenIgnoresUnknownClaims() {
        val extraClaims = mapOf(
            "exp" to EXP_SECONDS.toDouble(),
            "iat" to IAT_SECONDS.toDouble(),
            "iss" to "https://auth.example.com",
            "sub" to "550e8400-e29b-41d4-a716-446655440000",
            "aud" to JSONArray(listOf("klaviyo", "other-rp")),
            "nbf" to IAT_SECONDS.toDouble(),
            "jti" to "a8f5c1d4-3b6a-4c2e-9d1f-7e8b2a3c4d5e",
            "auth_time" to IAT_SECONDS.toDouble(),
            "azp" to "klaviyo-mobile-sdk",
            "email" to "user@example.com",
            "email_verified" to true,
            "scope" to "openid profile email",
            "address" to JSONObject(mapOf("country" to "US", "postal_code" to "12345"))
        )
        val token = makeJwt(extraClaims)

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertTrue(result is JWTValidationResult.Valid)
        val valid = result as JWTValidationResult.Valid
        assertEquals(EXP_SECONDS, valid.token.expiresAtEpochSeconds)
        assertEquals(IAT_SECONDS, valid.token.issuedAtEpochSeconds)
    }

    @Test
    fun validTokenDecodesFractionalNumericDate() {
        val iatFractional = IAT_SECONDS.toDouble() + 0.25
        val expFractional = EXP_SECONDS.toDouble() + 0.75
        val token = makeJwt(mapOf("exp" to expFractional, "iat" to iatFractional))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertTrue(result is JWTValidationResult.Valid)
        val valid = result as JWTValidationResult.Valid
        // Verify that the data class stores truncated Long values
        assertEquals(EXP_SECONDS, valid.token.expiresAtEpochSeconds)
        assertEquals(IAT_SECONDS, valid.token.issuedAtEpochSeconds)
    }

    // MARK: - Malformed structure

    @Test
    fun malformedStructureEmptyString() {
        val result = JWTParser.parseAndValidate("", nowEpochSeconds = NOW_SECONDS)
        assertEquals(JWTValidationResult.MalformedStructure, result)
    }

    @Test
    fun malformedStructureSingleSegment() {
        val result = JWTParser.parseAndValidate("only-one-segment", nowEpochSeconds = NOW_SECONDS)
        assertEquals(JWTValidationResult.MalformedStructure, result)
    }

    @Test
    fun malformedStructureTwoSegments() {
        val result = JWTParser.parseAndValidate("header.payload", nowEpochSeconds = NOW_SECONDS)
        assertEquals(JWTValidationResult.MalformedStructure, result)
    }

    @Test
    fun malformedStructureFourSegments() {
        val result = JWTParser.parseAndValidate("a.b.c.d", nowEpochSeconds = NOW_SECONDS)
        assertEquals(JWTValidationResult.MalformedStructure, result)
    }

    // MARK: - Malformed Base64

    @Test
    fun malformedBase64Payload() {
        val header = base64UrlEncode(JSONObject(mapOf("alg" to "HS256")).toString().toByteArray())
        val token = "$header.****.$header"

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MalformedBase64, result)
    }

    // MARK: - Malformed JSON

    @Test
    fun nonJsonPayload() {
        val header = base64UrlEncode(JSONObject(mapOf("alg" to "HS256")).toString().toByteArray())
        val payload = base64UrlEncode("not-actually-json".toByteArray())
        val token = "$header.$payload.$header"

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MalformedJson, result)
    }

    @Test
    fun jsonArrayPayload() {
        val header = base64UrlEncode(JSONObject(mapOf("alg" to "HS256")).toString().toByteArray())
        val payload = base64UrlEncode(JSONArray(listOf(1, 2, 3)).toString().toByteArray())
        val token = "$header.$payload.$header"

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MalformedJson, result)
    }

    @Test
    fun expAsString() {
        val token = makeJwt(mapOf("exp" to "not-a-number", "iat" to IAT_SECONDS.toDouble()))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MalformedJson, result)
    }

    @Test
    fun iatAsString() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to "not-a-number"))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MalformedJson, result)
    }

    @Test
    fun emptyPayloadSegment() {
        val header = base64UrlEncode(JSONObject(mapOf("alg" to "HS256")).toString().toByteArray())
        val token = "$header..$header"

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MalformedJson, result)
    }

    // MARK: - Missing claims

    @Test
    fun missingExpClaim() {
        val token = makeJwt(mapOf("iat" to IAT_SECONDS.toDouble()))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MissingExpClaim, result)
    }

    @Test
    fun missingIatClaim() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble()))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MissingIatClaim, result)
    }

    @Test
    fun emptyPayloadObject() {
        val token = makeJwt(emptyMap())

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertEquals(JWTValidationResult.MissingExpClaim, result)
    }

    // MARK: - Expiration

    @Test
    fun nowEqualsExpIsExpired() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = EXP_SECONDS)

        assertEquals(JWTValidationResult.ExpiredOnReceipt, result)
    }

    @Test
    fun nowGreaterThanExpIsExpired() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = EXP_SECONDS + 1)

        assertEquals(JWTValidationResult.ExpiredOnReceipt, result)
    }

    @Test
    fun nowAtLeewayBoundaryIsExpired() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))
        val leeway = 15L
        val nowAtBoundary = EXP_SECONDS - leeway

        val result = JWTParser.parseAndValidate(
            token,
            nowEpochSeconds = nowAtBoundary,
            leewaySeconds = leeway
        )

        assertEquals(JWTValidationResult.ExpiredOnReceipt, result)
    }

    @Test
    fun nowJustInsideLeewayIsValid() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))
        val leeway = 15L
        val nowJustInside = EXP_SECONDS - leeway - 1

        val result = JWTParser.parseAndValidate(
            token,
            nowEpochSeconds = nowJustInside,
            leewaySeconds = leeway
        )

        assertTrue(result is JWTValidationResult.Valid)
    }

    @Test
    fun leewayZeroBoundary() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))

        // now == exp - 1 is still valid
        val justBefore = JWTParser.parseAndValidate(
            token,
            nowEpochSeconds = EXP_SECONDS - 1,
            leewaySeconds = 0L
        )
        assertTrue(justBefore is JWTValidationResult.Valid)

        // now == exp is expired
        val atExp = JWTParser.parseAndValidate(
            token,
            nowEpochSeconds = EXP_SECONDS,
            leewaySeconds = 0L
        )
        assertEquals(JWTValidationResult.ExpiredOnReceipt, atExp)
    }

    @Test
    fun iatGreaterThanExpButNotExpiredIsValid() {
        val token = makeJwt(
            mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to EXP_SECONDS.toDouble() + 100)
        )

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertTrue(result is JWTValidationResult.Valid)
    }

    // MARK: - Base64URL handling

    @Test
    fun payloadWithUrlSafeCharactersDecodesCorrectly() {
        // Force URL-safe substitutions by choosing a claim value that produces
        // bytes that would be encoded with + or / in standard base64
        val token = makeJwt(
            mapOf(
                "exp" to EXP_SECONDS.toDouble(),
                "iat" to IAT_SECONDS.toDouble(),
                "data" to ">>>>???"
            )
        )

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertTrue(result is JWTValidationResult.Valid)
        val valid = result as JWTValidationResult.Valid
        assertEquals(EXP_SECONDS, valid.token.expiresAtEpochSeconds)
    }

    @Test
    fun payloadWithoutPaddingDecodesCorrectly() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))

        // Verify no = padding in the JWT
        assertFalse("JWT should not contain = padding", token.contains("="))

        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertTrue(result is JWTValidationResult.Valid)
    }

    // MARK: - Android-specific: toString redaction

    @Test
    fun validatedTokenToStringRedactsRawToken() {
        val token = makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to IAT_SECONDS.toDouble()))
        val result = JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)

        assertTrue(result is JWTValidationResult.Valid)
        val validatedToken = (result as JWTValidationResult.Valid).token
        val tokenString = validatedToken.toString()

        assertFalse("toString should not contain the raw JWT", tokenString.contains(token))
        assertTrue("toString should contain redaction marker", tokenString.contains("<redacted>"))
    }

    @Test
    fun failureLogsDoNotContainRawToken() {
        // Exercise every failure path and verify that no captured warning message
        // contains the raw JWT. Belt-and-suspenders for the "token contents NEVER
        // passed to the logger" requirement — code review alone is insufficient.
        val expiredHeader = base64UrlEncode(
            JSONObject(mapOf("alg" to "HS256")).toString().toByteArray()
        )
        val tokens = listOf(
            makeJwt(mapOf("iat" to IAT_SECONDS.toDouble())), // MissingExpClaim
            makeJwt(mapOf("exp" to EXP_SECONDS.toDouble())), // MissingIatClaim
            makeJwt(mapOf("exp" to "not-a-number", "iat" to IAT_SECONDS.toDouble())), // exp non-numeric
            makeJwt(mapOf("exp" to EXP_SECONDS.toDouble(), "iat" to "not-a-number")), // iat non-numeric
            "$expiredHeader.****.signature", // MalformedBase64
            "$expiredHeader.${base64UrlEncode("not-json".toByteArray())}.signature", // MalformedJson
            makeJwt(mapOf("exp" to NOW_SECONDS.toDouble() - 1, "iat" to IAT_SECONDS.toDouble())) // ExpiredOnReceipt
        )

        tokens.forEach { token ->
            JWTParser.parseAndValidate(token, nowEpochSeconds = NOW_SECONDS)
        }

        val messages = mutableListOf<String>()
        verify { spyLog.warning(capture(messages), null) }

        // Sanity: at least one warning per failure path.
        assertTrue(
            "Expected at least one warning per failure path",
            messages.size >= tokens.size
        )
        // The actual guarantee: no captured message contains any raw token.
        tokens.forEach { token ->
            messages.forEach { msg ->
                assertFalse(
                    "Log message must not contain raw JWT. Message: '$msg'",
                    msg.contains(token)
                )
            }
        }
    }

    // MARK: - Fixtures

    private fun makeJwt(claims: Map<String, Any>): String {
        val header = mapOf("alg" to "HS256", "typ" to "JWT")
        val headerSegment = base64UrlEncode(JSONObject(header).toString().toByteArray())
        val payloadSegment = base64UrlEncode(JSONObject(claims).toString().toByteArray())
        return "$headerSegment.$payloadSegment.signature"
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }
}
