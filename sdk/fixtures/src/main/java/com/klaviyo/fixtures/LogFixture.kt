package com.klaviyo.fixtures

import com.klaviyo.core.config.Log
import com.klaviyo.core.config.Log.Level

/**
 * Test fixture: Logger for unit tests of all build variants
 */
class LogFixture : Log {
    override var logLevel: Level = Level.Assert

    override fun verbose(message: String, ex: Throwable?) = println(message)

    override fun debug(message: String, ex: Throwable?) = println(message)

    override fun info(message: String, ex: Throwable?) = println(message)

    override fun warning(message: String, ex: Throwable?) = println(message)

    override fun error(message: String, ex: Throwable?) = println(message)

    override fun wtf(message: String, ex: Throwable?) = println(message)
}
