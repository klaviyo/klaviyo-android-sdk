package com.klaviyo.analytics.networking.requests

/**
 * For parsing Error responses from the backend when an HTTP request fails
 */

data class KlaviyoErrorResponse(
    val errors: List<KlaviyoError>
) {
    internal companion object {
        // Error body constants
        const val ERRORS = "errors"
        const val ID = "id"
        const val STATUS = "status"
        const val TITLE = "title"
        const val DETAIL = "detail"
        const val SOURCE = "source"
        const val POINTER = "pointer"
        const val INVALID_INPUT_TITLE = "Invalid input."
    }
}

data class KlaviyoError(
    val id: String? = null,
    val status: Int? = null,
    val title: String? = null,
    val detail: String? = null,
    val source: KlaviyoErrorSource? = null
)

data class KlaviyoErrorSource(
    val pointer: String? = null
) {
    internal companion object {
        // current path objects from the backend
        const val EMAIL_PATH = "attributes/email"
        const val PHONE_NUMBER_PATH = "attributes/phone_number"
    }
}
