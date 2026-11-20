package com.klaviyo.analytics.networking.requests

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Routes every [HttpUtil.openConnection] to one spied connection that answers with [code] and
 * [body]. Written request bodies are appended to [capturedBody]. Call `unmockkObject(HttpUtil)`
 * in teardown.
 */
internal fun mockInboxConnection(
    baseUrl: String,
    code: Int = 200,
    body: String = "{}",
    headerFields: Map<String?, List<String>> = emptyMap(),
    capturedBody: MutableList<String> = mutableListOf()
): HttpURLConnection {
    val connection = spyk(URL(baseUrl).openConnection()) as HttpURLConnection
    mockkObject(HttpUtil)
    every { HttpUtil.openConnection(any()) } returns connection
    every { HttpUtil.writeToConnection(capture(capturedBody), connection) } returns Unit
    every { connection.connect() } returns Unit
    every { connection.responseCode } returns code
    every { connection.headerFields } returns headerFields
    every { connection.inputStream } answers { ByteArrayInputStream(body.toByteArray()) }
    every { connection.errorStream } answers { ByteArrayInputStream(body.toByteArray()) }
    return connection
}
