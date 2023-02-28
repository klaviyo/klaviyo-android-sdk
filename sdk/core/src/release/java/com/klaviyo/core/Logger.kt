package com.klaviyo.core

import com.klaviyo.core.config.Log
import com.klaviyo.core.config.NetworkRequest

internal class Logger : Log {
    override fun debug(output: String) {}
    override fun info(output: String) {}
    override fun error(output: String) {}
    override fun exception(exception: Exception, message: String?) {}
    override fun onLifecycleEvent(event: String) {}
    override fun onNetworkChange(connected: Boolean) {}
    override fun onApiRequest(request: NetworkRequest) {}
}
