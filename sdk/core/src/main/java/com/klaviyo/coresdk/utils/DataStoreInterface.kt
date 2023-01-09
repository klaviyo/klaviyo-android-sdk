package com.klaviyo.coresdk.utils

/**
 * TODO asynchronous?
 */
internal interface DataStoreInterface {
    fun fetch(key: String): String?

    fun store(key: String, value: String)
}
