package com.klaviyo.inbox

/**
 * Why an inbox call failed
 */
class InboxException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class Reason {
        /** The on-device store could not complete the call */
        STORAGE_FAILURE
    }
}

/**
 * The outcome of a callback-style call from Java
 */
sealed class InboxResult<out T> {
    class Success<out T>(val value: T) : InboxResult<T>() {
        override fun toString(): String = "Success($value)"
    }

    class Failure(val exception: InboxException) : InboxResult<Nothing>() {
        override fun toString(): String = "Failure(${exception.reason})"
    }
}

/** Receives the result of [KlaviyoInbox.fetchUnreadCount] on the main thread */
fun interface InboxCountCallback {
    fun onResult(result: InboxResult<Int>)
}
