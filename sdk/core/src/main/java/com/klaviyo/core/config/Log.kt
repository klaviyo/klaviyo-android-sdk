package com.klaviyo.core.config

import com.klaviyo.core.utils.AdvancedAPI

interface Log {

    /**
     * Log levels enum
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
     * The minimum log level to write to system logs
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
     * Register a log interceptor to be notified of all log calls (regardless of level).
     *
     * @param interceptor The interceptor to be invoked for every log event
     */
    @AdvancedAPI
    fun addInterceptor(interceptor: LogInterceptor)

    /**
     * Unregister a previously registered log interceptor
     *
     * @param interceptor The interceptor to remove
     */
    @AdvancedAPI
    fun removeInterceptor(interceptor: LogInterceptor)

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

/**
 * Interceptor for log events. Allows custom logging hooks to be built on top of the default behavior
 *
 * Example usage:
 * ```
 * KLog.addInterceptor { level, tag, message, throwable ->
 *     // Log to file, send to analytics, etc.
 *     // Filter by level, if desired
 * }
 * ```
 */
typealias LogInterceptor = (level: Log.Level, tag: String, message: String, throwable: Throwable?) -> Unit
