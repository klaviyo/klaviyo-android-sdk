package com.klaviyo.core

import com.klaviyo.core.config.Log
import com.klaviyo.core.config.NetworkRequest
import java.lang.Exception

internal object Logger : Log {
    override fun debug(output: String) {
        println(output)
    }

    override fun info(output: String) {
        println(output)
    }

    override fun error(output: String) {
        println(output)
    }

    override fun exception(exception: Exception, message: String?) {
        println("${exception.message} $message")
    }

    override fun onNetworkRequest(request: NetworkRequest) {
        println("${request.httpMethod} to ${request.url} ${request.state}")
    }
}

internal object Console {
    fun log(string: String) = println(string)
}
