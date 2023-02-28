package com.klaviyo.core

import android.os.Build
import android.util.Log.d
import android.util.Log.e
import android.util.Log.i
import android.util.Log.wtf
import com.klaviyo.core.config.Log
import com.klaviyo.core.config.NetworkRequest
import java.lang.Exception
import java.util.regex.Pattern

open class Logger : Log {
    override fun debug(message: String, ex: Exception?) {
        Console.log(message, Console.Level.Debug, makeTag(), ex)
    }

    override fun info(message: String, ex: Exception?) {
        Console.log(message, Console.Level.Info, makeTag(), ex)
    }

    override fun error(message: String, ex: Exception?) {
        Console.log(message, Console.Level.Error, makeTag(), ex)
    }

    override fun wtf(message: String, ex: Exception?) {
        Console.log(message, Console.Level.Assert, makeTag(), ex)
    }

    override fun onLifecycleEvent(event: String) = debug(event)

    override fun onNetworkChange(connected: Boolean) =
        debug("Internet connection is " + (if (connected) "available" else "unavailable"))

    override fun onApiRequest(request: NetworkRequest) =
        debug("${request.httpMethod} to ${request.url} ${request.state}")

    /**
     * Inspired from reading through Timber source code
     * We really don't need the full dependency though
     */
    private companion object {
        private const val MAX_TAG_LENGTH = 23
        private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")

        private val ignoreList = listOf(
            Logger::class.java.name,
            Console::class.java.name
        )

        /**
         * Extract the tag which should be used for the message from the `element`. By default
         * this will use the class name without any anonymous class suffixes (e.g., `Foo$1`
         * becomes `Foo`).
         *
         * Note: This will not be called if a manual tag is specified
         */
        private fun makeTag(): String = Throwable().stackTrace
            .first { it.className !in ignoreList }
            .let { element ->
                var tag = element.className.substringAfterLast('.')
                val m = ANONYMOUS_CLASS.matcher(tag)
                if (m.find()) {
                    tag = m.replaceAll("")
                }
                // Tag length limit was removed in API 26.
                return if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= 26) {
                    tag
                } else {
                    tag.substring(0, MAX_TAG_LENGTH)
                }
            }
    }
}

/**
 * Android Log output wrapper
 */
internal object Console {
    enum class Level {
        Debug, Info, Error, Assert
    }

    fun log(msg: String, level: Level, tag: String, ex: Throwable? = null) = when (level) {
        Level.Debug -> ex?.let { d(tag, msg, ex) } ?: d(tag, msg)
        Level.Info -> ex?.let { i(tag, msg, ex) } ?: i(tag, msg)
        Level.Error -> ex?.let { e(tag, msg, ex) } ?: e(tag, msg)
        Level.Assert -> ex?.let { wtf(tag, msg, ex) } ?: wtf(tag, msg)
    }
}
