package com.klaviyo.core

import android.os.Build
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.Log
import com.klaviyo.core.config.Log.Level
import java.util.regex.Pattern

object KLog : Log {

    /**
     * Log level key for manifest, specifying the minimum log level to output, see [Log.logLevel]
     */
    private const val LOG_LEVEL = "com.klaviyo.core.log_level"

    private const val MAX_TAG_LENGTH = 23

    private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")

    private val ignoreList = listOf(
        KLog::class.java.name
    )

    private val defaultLogLevel = if (BuildConfig.DEBUG) {
        Level.Warning
    } else {
        Level.Error
    }

    private var _logLevel: Level? = null
    override var logLevel: Level
        get() {
            if (_logLevel == null && Registry.isRegistered<Config>()) {
                _logLevel = Registry.config.getManifestInt(LOG_LEVEL, defaultLogLevel.ordinal).let {
                    Level.entries.getOrNull(it) ?: defaultLogLevel.also { default ->
                        default.log(
                            "KlaviyoLog",
                            "Invalid log level $it detected in manifest, defaulting to $default."
                        )
                    }
                }
            }

            return _logLevel ?: defaultLogLevel
        }
        set(value) {
            _logLevel = value
        }

    override fun wtf(message: String, ex: Throwable?) = log(message, Level.Assert, ex)

    override fun error(message: String, ex: Throwable?) = log(message, Level.Error, ex)

    override fun warning(message: String, ex: Throwable?) = log(message, Level.Warning, ex)

    override fun info(message: String, ex: Throwable?) = log(message, Level.Info, ex)

    override fun debug(message: String, ex: Throwable?) = log(message, Level.Debug, ex)

    override fun verbose(message: String, ex: Throwable?) = log(message, Level.Verbose, ex)

    fun log(msg: String, level: Level, ex: Throwable? = null) {
        if (logLevel == Level.None) return

        if (level.ordinal >= logLevel.ordinal) {
            level.log(makeTag(), msg, ex)
        }
    }

    /**
     * Inspired from reading through Timber source code
     * We really don't need the full dependency though
     *
     * Extract the tag which should be used for the message from the `element`. By default,
     * this will use the class name without any anonymous class suffixes (e.g., `Foo$1`
     * becomes `Foo`).
     *
     * NOTE: This will not be called if a manual tag is specified
     */
    private fun makeTag(): String = Throwable().stackTrace
        .first { it.className !in ignoreList && !it.className.contains(Log::class.java.name) }
        .let { element ->
            var tag = element.className.substringAfterLast('.')
            tag = if (element.methodName == "invoke") "$tag:${element.lineNumber}" else tag

            val m = ANONYMOUS_CLASS.matcher(tag)
            if (m.find()) {
                tag = m.replaceAll("")
            }

            // Make sure tag contains our name
            if (!tag.lowercase().contains("klaviyo")) {
                tag = "Klaviyo.$tag"
            }

            // Tag length limit was removed in API 26.
            tag = if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= 26) {
                tag
            } else {
                tag.substring(0, MAX_TAG_LENGTH)
            }

            return tag
        }
}
