package com.klaviyo.fixtures

import com.klaviyo.core.config.Log
import com.klaviyo.core.config.Log.Level
import com.klaviyo.core.config.LogInterceptor
import com.klaviyo.core.utils.AdvancedAPI

/**
 * Test fixture: Logger for unit tests of all build variants
 */
class LogFixture : Log {
    override var logLevel: Level = Level.Assert

    private val _interceptors = mutableListOf<LogInterceptor>()

    /**
     * List of currently registered interceptors for test verification
     */
    val interceptors: List<LogInterceptor> get() = _interceptors.toList()

    @AdvancedAPI
    override fun addInterceptor(interceptor: LogInterceptor) {
        _interceptors.add(interceptor)
    }

    @AdvancedAPI
    override fun removeInterceptor(interceptor: LogInterceptor) {
        _interceptors.remove(interceptor)
    }

    override fun verbose(message: String, ex: Throwable?) = println(message)

    override fun debug(message: String, ex: Throwable?) = println(message)

    override fun info(message: String, ex: Throwable?) = println(message)

    override fun warning(message: String, ex: Throwable?) = println(message)

    override fun error(message: String, ex: Throwable?) = println(message)

    override fun wtf(message: String, ex: Throwable?) = println(message)
}
