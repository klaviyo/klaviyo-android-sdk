package com.klaviyo.coresdk

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigFileUtilsTest {
    private val contextMock = mock<Context>()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        KlaviyoConfig.Builder()
                .apiKey("Fake_Key")
                .applicationContext(contextMock)
                .networkTimeout(1000)
                .networkFlushInterval(10000)
                .build()
    }

    @Test
    fun `Find UUID from config file successfully`() {
        val file = temporaryFolder.newFile()
        file.writeText("uuid:a123")

        val uuid = ConfigFileUtils.findConfigValue(file, "uuid")

        assertEquals("a123", uuid)
    }

    @Test
    fun `Write UUID to config file successfully`() {
        val file = temporaryFolder.newFile()

        ConfigFileUtils.writeConfigValue(file, "uuid", "a123")

        assertEquals("uuid:a123", file.readText())
    }
}