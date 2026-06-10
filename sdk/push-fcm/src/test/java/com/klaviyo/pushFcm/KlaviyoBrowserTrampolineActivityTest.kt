package com.klaviyo.pushFcm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class KlaviyoBrowserTrampolineActivityTest : BaseTest() {

    private val mockBrowserIntent = mockk<Intent>(relaxed = true)
    private val trampolineContextPackageManager = mockk<PackageManager>(relaxed = true)
    private val mockTrampolineContext = mockk<Context>(relaxed = true).apply {
        every { packageManager } returns trampolineContextPackageManager
        every { startActivity(any()) } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()
        mockkObject(Klaviyo)
        mockkObject(DeepLinking)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { Klaviyo.handlePush(any()) } returns Klaviyo
        every { DeepLinking.makeBrowserIntent(any()) } returns mockBrowserIntent
        // Make the browser intent appear resolvable so startActivityIfResolved
        // actually dispatches to context.startActivity (vs logging an error).
        every { mockBrowserIntent.resolveActivity(any()) } returns mockk()
    }

    @Test
    fun `handleTrampolineIntent calls handlePush and launches browser intent`() {
        val mockIntent = mockk<Intent>(relaxed = true)
        every {
            mockIntent.getStringExtra(KlaviyoBrowserTrampolineActivity.BROWSER_URL_EXTRA)
        } returns "https://example.com"

        KlaviyoBrowserTrampolineActivity.handleTrampolineIntent(mockIntent, mockTrampolineContext)

        verify { Klaviyo.handlePush(mockIntent) }
        verify { DeepLinking.makeBrowserIntent(any()) }
        verify { mockTrampolineContext.startActivity(mockBrowserIntent) }
    }

    @Test
    fun `handleTrampolineIntent without URL extra logs warning and does not launch`() {
        val mockIntent = mockk<Intent>(relaxed = true)
        every {
            mockIntent.getStringExtra(KlaviyoBrowserTrampolineActivity.BROWSER_URL_EXTRA)
        } returns null

        KlaviyoBrowserTrampolineActivity.handleTrampolineIntent(mockIntent, mockTrampolineContext)

        verify { Klaviyo.handlePush(mockIntent) }
        verify(exactly = 0) { DeepLinking.makeBrowserIntent(any()) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `handleTrampolineIntent with null intent logs warning`() {
        KlaviyoBrowserTrampolineActivity.handleTrampolineIntent(null, mockTrampolineContext)

        verify { Klaviyo.handlePush(null) }
        verify(exactly = 0) { DeepLinking.makeBrowserIntent(any()) }
        verify(exactly = 0) { mockTrampolineContext.startActivity(any()) }
        verify { spyLog.warning(any(), null) }
    }
}
