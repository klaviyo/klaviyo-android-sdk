package com.klaviyo.pushFcm

import android.content.Intent
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class KlaviyoBrowserTrampolineActivityTest : BaseTest() {

    private val mockBrowserIntent = mockk<Intent>(relaxed = true)

    @Before
    override fun setup() {
        super.setup()
        MockIntent.setupIntentMocking()
        mockkObject(Klaviyo)
        mockkObject(DeepLinking)
        every { Klaviyo.handlePush(any()) } returns Klaviyo
        every { DeepLinking.makeBrowserIntent(any()) } returns mockBrowserIntent
    }

    @After
    override fun cleanup() {
        super.cleanup()
    }

    @Test
    fun `handleTrampolineIntent calls handlePush and launches browser intent`() {
        val mockIntent = mockk<Intent>(relaxed = true)
        every {
            mockIntent.getStringExtra(KlaviyoBrowserTrampolineActivity.BROWSER_URL_EXTRA)
        } returns "https://example.com"

        val activity = spyk(KlaviyoBrowserTrampolineActivity()).apply {
            every { intent } returns mockIntent
            every { startActivity(any()) } returns Unit
        }

        activity.handleTrampolineIntent()

        verify { Klaviyo.handlePush(mockIntent) }
        verify { DeepLinking.makeBrowserIntent(any()) }
        verify { activity.startActivity(mockBrowserIntent) }
    }

    @Test
    fun `handleTrampolineIntent without URL extra logs warning and does not launch`() {
        val mockIntent = mockk<Intent>(relaxed = true)
        every {
            mockIntent.getStringExtra(KlaviyoBrowserTrampolineActivity.BROWSER_URL_EXTRA)
        } returns null

        val activity = spyk(KlaviyoBrowserTrampolineActivity()).apply {
            every { intent } returns mockIntent
            every { startActivity(any()) } returns Unit
        }

        activity.handleTrampolineIntent()

        verify { Klaviyo.handlePush(mockIntent) }
        verify(exactly = 0) { DeepLinking.makeBrowserIntent(any()) }
        verify(exactly = 0) { activity.startActivity(any()) }
        verify { spyLog.warning(any()) }
    }
}
