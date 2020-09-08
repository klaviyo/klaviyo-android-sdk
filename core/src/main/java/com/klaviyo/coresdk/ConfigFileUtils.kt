package com.klaviyo.coresdk

import java.io.File
import java.util.*

internal object ConfigFileUtils {
    private const val CONFIG_FILE_NAME = "klaviyo-sdk-config"

    private const val UUID_KEY = "uuid"

    fun readOrCreateUUID(): String {
        val file = File("${KlaviyoConfig.applicationContext.filesDir}/$CONFIG_FILE_NAME")

        var uuid = findConfigValue(file, UUID_KEY)

        if (uuid.isNullOrEmpty()) {
            uuid = UUID.randomUUID().toString()
            writeConfigValue(file, UUID_KEY, uuid)
        }

        return uuid
    }

    fun findConfigValue(file: File, key: String): String? {
        var value: String? = null

        if (!file.exists()) {
            return null
        }

        file.useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("$key:")) {
                    value = line.substring("$key:".length)

                }
            }
        }
        return value
    }

    fun writeConfigValue(file: File, key:String, value: String) {
        file.writeText("$key:$value")
    }
}