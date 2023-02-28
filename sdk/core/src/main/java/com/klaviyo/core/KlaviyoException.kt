package com.klaviyo.core

/**
 * Exceptions that automatically hook into our logger
 *
 * @property message
 */
abstract class KlaviyoException(final override val message: String) : Exception(message) {
    init {
        log()
    }

    private fun log() = Registry.log.wtf(message, this)
}
