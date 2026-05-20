package com.klaviyo.analytics.auth

internal sealed interface JWTValidationResult {
    data class Valid(val token: ValidatedToken) : JWTValidationResult
    data object MalformedStructure : JWTValidationResult
    data object MalformedBase64 : JWTValidationResult
    data object MalformedJson : JWTValidationResult
    data object MissingExpClaim : JWTValidationResult
    data object MissingIatClaim : JWTValidationResult
    data object ExpiredOnReceipt : JWTValidationResult
}
