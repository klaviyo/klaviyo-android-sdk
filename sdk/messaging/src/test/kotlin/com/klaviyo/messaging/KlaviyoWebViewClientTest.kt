package com.klaviyo.messaging

import android.content.Intent
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.LogFixture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

class KlaviyoWebViewClientTest {

    private lateinit var client: KlaviyoWebViewClient
    private val mockOnShow = mockk<() -> Unit>(relaxed = true)
    private val mockOnClose = mockk<() -> Unit>(relaxed = true)
    private val mockEvaluateJs = mockk<(String, (String) -> Unit) -> Unit>(relaxed = true)
    private val mockLaunchWebViewUrl = mockk<(Intent) -> Unit>(relaxed = true)
    private val spyLog = spyk(LogFixture())

    @Before
    fun setUp() {
        client = KlaviyoWebViewClient(
            onShow = mockOnShow,
            onClose = mockOnClose,
            evaluateJs = mockEvaluateJs,
            launchWebViewUrl = mockLaunchWebViewUrl
        )
        mockkObject(Registry)
        every { Registry.log } returns spyLog
    }

    @Test
    fun `postMessage with documentReady type calls onShow`() {
        val message = JSONObject().put("type", "documentReady").toString()

        client.postMessage(message)

        verify { mockOnShow.invoke() }
    }

    @Test
    fun `postMessage with close type calls onClose`() {
        val message = JSONObject().put("type", "close").toString()

        client.postMessage(message)

        verify { mockOnClose.invoke() }
    }

    @Test
    fun `postMessage with console type logs to console`() {
        val jsonData = JSONObject().put("level", "log").put("message", "web is for clowns")
        val message = JSONObject().put("type", "console").put("data", jsonData).toString()

        client.postMessage(message)

        verify { spyLog.info(eq("web is for clowns")) }
    }
}
