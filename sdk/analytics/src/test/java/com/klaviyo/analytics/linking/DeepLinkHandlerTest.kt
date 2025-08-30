package com.klaviyo.analytics.linking

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.klaviyo.core.BuildConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class DeepLinkHandlerTest : BaseTest() {

    private val testUrl = "https://example.com/u/slug"
    private val mockUri = mockk<Uri>(relaxed = true)
    private val uriSlot = slot<Uri>()
    private val actionSlot = slot<String>()
    private val packageSlot = slot<String>()
    private val flagsSlot = slot<Int>()

    @Before
    override fun setup() {
        super.setup()

        mockkStatic(Uri::class)
        every { Uri.parse(testUrl) } returns mockUri

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setData(capture(uriSlot)) } returns mockk<Intent>()
        every { anyConstructed<Intent>().setAction(capture(actionSlot)) } returns mockk<Intent>()
        every { anyConstructed<Intent>().setPackage(capture(packageSlot)) } returns mockk<Intent>()
        every { anyConstructed<Intent>().setFlags(capture(flagsSlot)) } returns mockk<Intent>()
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkAll()
        Registry.unregister<DeepLinkHandler>()
    }

    @Test
    fun `test handleDeepLink for a URL with registered handler`() {
        val deepLinkHandler: DeepLinkHandler = mockk(relaxed = true)
        Registry.register<DeepLinkHandler>(deepLinkHandler)

        DeepLinking.handleDeepLink(testUrl)

        // Verify that the handler was invoked with the correct URI
        verify { deepLinkHandler.invoke(mockUri) }
    }

    @Test
    fun `test handleDeepLink handles a bad url`() {
        every { Uri.parse(any()) } throws IllegalArgumentException("Invalid URI")

        val deepLinkHandler: DeepLinkHandler = mockk(relaxed = true)
        Registry.register<DeepLinkHandler>(deepLinkHandler)

        DeepLinking.handleDeepLink("invalid_url")

        // Verify that the handler wasn't with the correct URI but the exception was caught
        verify(inverse = true) { deepLinkHandler.invoke(mockUri) }
    }

    @Test
    fun `test handleDeepLink without registered handler`() {
        val mockActivity = mockk<Activity>(relaxed = true)
        every { mockActivity.startActivity(any()) } returns Unit
        Registry.lifecycleMonitor.runWithCurrentOrNextActivity(50L) { mockActivity }

        DeepLinking.handleDeepLink(mockUri)

        // Verify that the intent was created with the correct properties
        verify {
            anyConstructed<Intent>().setData(mockUri)
            anyConstructed<Intent>().setAction(Intent.ACTION_VIEW)
            anyConstructed<Intent>().setPackage(BuildConfig.LIBRARY_PACKAGE_NAME)
            anyConstructed<Intent>().setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
