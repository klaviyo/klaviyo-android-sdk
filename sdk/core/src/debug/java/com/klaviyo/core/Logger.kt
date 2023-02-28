package com.klaviyo.core

import com.klaviyo.core.config.Log
import com.klaviyo.core.config.NetworkRequest
import java.lang.Exception

open class Logger : Log {
    override fun debug(output: String) {
        Console.log(output)
    }

    override fun info(output: String) {
        Console.log(output)
    }

    override fun error(output: String) {
        Console.log(output)
    }

    override fun exception(exception: Exception, message: String?) {
        Console.log("${exception.message} $message")
    }

    override fun onNetworkRequest(request: NetworkRequest) {
        Console.log("${request.httpMethod} to ${request.url} ${request.state}")
    }
}

internal object Console {
    fun log(string: String) = println(string)
}
