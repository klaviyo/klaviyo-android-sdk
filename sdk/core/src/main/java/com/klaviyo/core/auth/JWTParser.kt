package com.klaviyo.core.auth

import android.util.Base64
import com.klaviyo.core.Registry
import org.json.JSONException
import org.json.JSONObject

internal object JWTParser {
    const val DEFAULT_LEEWAY_SECONDS: Long = 30L

    fun parseAndValidate(
        token: String,
        nowEpochSeconds: Long = Registry.clock.currentTimeMillis() / 1000L,
        leewaySeconds: Long = DEFAULT_LEEWAY_SECONDS
    ): JWTValidationResult {
        val segments = token.split('.')
        if (segments.size != 3) {
            Registry.log.warning("JWT validation failed: malformed structure")
            return JWTValidationResult.MalformedStructure
        }

        val payloadBytes = base64UrlDecode(segments[1]) ?: run {
            Registry.log.warning("JWT validation failed: malformed base64URL payload")
            return JWTValidationResult.MalformedBase64
        }

        val claims = try {
            JSONObject(String(payloadBytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            Registry.log.warning("JWT validation failed: malformed JSON payload")
            return JWTValidationResult.MalformedJson
        }

        val expRaw = claims.numericClaim("exp")
        if (expRaw == null) {
            Registry.log.warning("JWT validation failed: missing exp claim")
            return JWTValidationResult.MissingExpClaim
        }
        if (expRaw.isNaN()) {
            Registry.log.warning("JWT validation failed: exp claim non-numeric")
            return JWTValidationResult.MalformedJson
        }

        val iatRaw = claims.numericClaim("iat")
        if (iatRaw == null) {
            Registry.log.warning("JWT validation failed: missing iat claim")
            return JWTValidationResult.MissingIatClaim
        }
        if (iatRaw.isNaN()) {
            Registry.log.warning("JWT validation failed: iat claim non-numeric")
            return JWTValidationResult.MalformedJson
        }

        if (nowEpochSeconds.toDouble() >= expRaw - leewaySeconds.toDouble()) {
            Registry.log.warning("JWT validation failed: expired on receipt")
            return JWTValidationResult.ExpiredOnReceipt
        }

        return JWTValidationResult.Valid(
            ValidatedToken(
                rawToken = token,
                expiresAtEpochSeconds = expRaw.toLong(),
                issuedAtEpochSeconds = iatRaw.toLong()
            )
        )
    }

    private fun base64UrlDecode(value: String): ByteArray? {
        val normalized = value
            .replace('-', '+')
            .replace('_', '/')
        val paddingNeeded = (4 - normalized.length % 4) % 4
        val padded = normalized + "=".repeat(paddingNeeded)
        return try {
            Base64.decode(padded, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun JSONObject.numericClaim(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val raw = get(key)
        return when (raw) {
            is Number -> raw.toDouble()
            else -> Double.NaN
        }
    }
}
