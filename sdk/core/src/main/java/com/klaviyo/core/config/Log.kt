package com.klaviyo.core.config

interface Log {

    /**
     * Log
     */
    enum class Level {
        None,
        Verbose,
        Debug,
        Info,
        Warning,
        Error,
        Assert
    }

    /**
     * The minimum log level to output
     *
     * To configure from manifest, specify the minimum log level to output:
     *
     * 0 = Disable logging
     * 1 = Verbose and above
     * 2 = Debug and above
     * 3 = Info and above
     * 4 = Error and above
     * 5 = Warning and above
     * 6 = Assert only
     */
    var logLevel: Level

    /**
     * Verbose output
     *
     * @param message
     * @param ex
     */
    fun verbose(message: String, ex: Throwable? = null)

    /**
     * Debugging output
     *
     * @param message
     * @param ex
     */
    fun debug(message: String, ex: Throwable? = null)

    /**
     * Informational output
     *
     * @param message
     * @param ex
     */
    fun info(message: String, ex: Throwable? = null)

    /**
     * Encountered a potential problem
     *
     * @param message
     * @param ex
     */
    fun warning(message: String, ex: Throwable? = null)

    /**
     * Encountered an error or exception
     *
     * @param message
     * @param ex
     */
    fun error(message: String, ex: Throwable? = null)

    /**
     * Encountered a completely unexpected scenario
     *
     * @param message
     * @param ex
     */
    fun wtf(message: String, ex: Throwable? = null)
}
