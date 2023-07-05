package com.klaviyo.core

import com.klaviyo.core.config.Log

internal class Logger : Log {
    override fun verbose(message: String, ex: Throwable?) {}
    override fun debug(message: String, ex: Throwable?) {}
    override fun info(message: String, ex: Throwable?) {}
    override fun error(message: String, ex: Throwable?) {}
    override fun wtf(message: String, ex: Throwable?) {}
}
