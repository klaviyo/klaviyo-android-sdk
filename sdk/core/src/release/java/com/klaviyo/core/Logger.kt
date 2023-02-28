package com.klaviyo.core

import com.klaviyo.core.config.Log
import com.klaviyo.core.config.NetworkRequest

internal object Logger : Log {
    override fun debug(output: String) {}
    override fun info(output: String) {}
    override fun error(output: String) {}
    override fun exception(exception: Exception, message: String?) {}
    override fun onNetworkRequest(request: NetworkRequest) {}
}
