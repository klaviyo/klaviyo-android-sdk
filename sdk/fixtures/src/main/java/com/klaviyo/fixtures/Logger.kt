package com.klaviyo.fixtures

import com.klaviyo.core.config.Log

/**
 * Test fixture: Logger for unit tests of all build variants
 */
class Logger : Log {
    override fun verbose(message: String, ex: Exception?) { println(message) }
    override fun debug(message: String, ex: Exception?) { println(message) }
    override fun info(message: String, ex: Exception?) { println(message) }
    override fun error(message: String, ex: Exception?) { println(message) }
    override fun wtf(message: String, ex: Exception?) { println(message) }
}
