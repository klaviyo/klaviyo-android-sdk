package com.klaviyo.coresdk.utils

import com.klaviyo.coresdk.KlaviyoConfig
import java.io.File
import java.util.UUID

internal object ConfigFileUtils {
    private const val CONFIG_FILE_NAME = "klaviyo-sdk-config"

    private fun openConfigFile(): File {
        return File("${KlaviyoConfig.applicationContext.filesDir}/$CONFIG_FILE_NAME")
    }

    fun readOrCreateUUID(): String {
        val file = openConfigFile()

        var uuid = findConfigValue(file, ConfigKeys.UUID_KEY)

        if (uuid.isEmpty()) {
            uuid = UUID.randomUUID().toString()
            writeConfigValue(file, ConfigKeys.UUID_KEY, uuid)
        }

        return uuid
    }

    fun readValue(key: String): String {
        return findConfigValue(openConfigFile(), key)
    }

    fun findConfigValue(file: File, key: String): String {
        var value = ""

        if (!file.exists()) {
            return value
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

    fun writeConfigValue(key: String, value: String) {
        writeConfigValue(openConfigFile(), key, value)
    }

    fun writeConfigValue(file: File, key: String, value: String) {
        file.appendText("$key:$value\n")
    }
}