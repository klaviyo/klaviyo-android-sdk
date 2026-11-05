package com.klaviyo.inbox

/**
 * Why an inbox call failed
 */
sealed class InboxException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** The on-device store could not complete the call */
    class StorageFailure(
        message: String,
        cause: Throwable? = null
    ) : InboxException(message, cause)

    /** The cursor is malformed, or did not come from a page of this inbox */
    class InvalidCursor(
        message: String,
        cause: Throwable? = null
    ) : InboxException(message, cause)
}

/**
 * The outcome of a callback-style call from Java
 */
sealed class InboxResult<out T> {
    class Success<out T>(val value: T) : InboxResult<T>() {
        override fun toString(): String = "Success($value)"
    }

    class Failure(val exception: InboxException) : InboxResult<Nothing>() {
        override fun toString(): String = "Failure(${exception::class.simpleName})"
    }
}

/** Receives the result of [KlaviyoInbox.fetchPage] on the main thread */
fun interface InboxPageCallback {
    fun onResult(result: InboxResult<InboxPage>)
}

/** Receives the result of [KlaviyoInbox.fetchUnreadCount] on the main thread */
fun interface InboxCountCallback {
    fun onResult(result: InboxResult<Int>)
}
