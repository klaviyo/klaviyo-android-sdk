package com.klaviyo.core_shared_tests // ktlint-disable package-name

import com.klaviyo.core.config.Log
import com.klaviyo.core.networking.NetworkRequest

/**
 * Test fixture: Logger for unit tests of all build variants
 */
class TestLogger : Log {
    override fun debug(message: String, ex: Exception?) { println(message) }
    override fun info(message: String, ex: Exception?) { println(message) }
    override fun error(message: String, ex: Exception?) { println(message) }
    override fun wtf(message: String, ex: Exception?) { println(message) }

    override fun onLifecycleEvent(event: String) { println(event) }
    override fun onNetworkChange(connected: Boolean) { println(connected) }
    override fun onApiRequest(request: NetworkRequest) { println(request) }
    override fun onDataStore(key: String, value: String?) { println("$key=$value") }
}
