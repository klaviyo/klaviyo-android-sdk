package com.klaviyo.core

import com.klaviyo.core.config.Log

internal class Logger : Log {
    override fun debug(message: String, ex: Exception?) {}
    override fun info(message: String, ex: Exception?) {}
    override fun error(message: String, ex: Exception?) {}
    override fun wtf(message: String, ex: Exception?) {}
}
