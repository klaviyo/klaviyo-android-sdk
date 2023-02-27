package com.klaviyo.core

object Logger : com.klaviyo.core.config.Log {
    override fun debug(output: String) { println(output) }
}
