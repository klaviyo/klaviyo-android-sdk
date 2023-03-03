package com.klaviyo.core.config

interface Log {

    /**
     * Verbose output
     *
     * @param message
     * @param ex
     */
    fun verbose(message: String, ex: Exception? = null)

    /**
     * Debugging output
     *
     * @param message
     * @param ex
     */
    fun debug(message: String, ex: Exception? = null)

    /**
     * Informational output
     *
     * @param message
     * @param ex
     */
    fun info(message: String, ex: Exception? = null)

    /**
     * Encountered an error or exception
     *
     * @param message
     * @param ex
     */
    fun error(message: String, ex: Exception? = null)

    /**
     * Encountered a completely unexpected scenario
     *
     * @param message
     * @param ex
     */
    fun wtf(message: String, ex: Exception? = null)
}
