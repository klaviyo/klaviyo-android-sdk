package com.klaviyo.core

import com.klaviyo.core.config.Log
import com.klaviyo.core.config.NetworkRequest
import com.klaviyo.core.networking.NetworkRequest

internal class Logger : Log {
    override fun debug(message: String, ex: Exception?) {}
    override fun info(message: String, ex: Exception?) {}
    override fun error(message: String, ex: Exception?) {}
    override fun wtf(message: String, ex: Exception?) {}

    override fun onLifecycleEvent(event: String) {}
    override fun onNetworkChange(connected: Boolean) {}
    override fun onApiRequest(request: NetworkRequest) {}
    override fun onDataStore(key: String, value: String?) {}
}
