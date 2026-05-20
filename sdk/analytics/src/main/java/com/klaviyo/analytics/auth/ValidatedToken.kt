package com.klaviyo.analytics.auth

internal data class ValidatedToken(
    val rawToken: String,
    val expiresAtEpochSeconds: Long,
    val issuedAtEpochSeconds: Long
) {
    // Defensive redaction: prevents the JWT from being exposed if any future call
    // site ever logs a ValidatedToken (e.g. `log.debug("got $token")`). Token
    // contents must never be passed to the logger under any circumstances.
    override fun toString(): String =
        "ValidatedToken(rawToken=<redacted>, " +
            "expiresAtEpochSeconds=$expiresAtEpochSeconds, " +
            "issuedAtEpochSeconds=$issuedAtEpochSeconds)"
}
