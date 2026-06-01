package com.klaviyo.core.auth

/**
 * A JWT that has been parsed and validated by [JWTParser]. Returned by
 * [AuthTokenManager.currentToken] so consumers get both the raw token (for transport/injection)
 * and the parsed claim metadata (`exp`, `iat`) without re-parsing.
 *
 * Public so cross-module consumers (e.g. the forms module's WebView injection layer) can read
 * the metadata; [toString] is redacted to keep the raw token out of accidental log statements.
 */
data class ValidatedToken(
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
