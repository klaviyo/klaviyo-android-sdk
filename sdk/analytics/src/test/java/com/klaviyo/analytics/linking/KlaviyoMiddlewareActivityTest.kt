package com.klaviyo.analytics.linking

import android.content.Intent
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.BuildConfig
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class KlaviyoMiddlewareActivityTest : BaseTest() {

    @Before
    override fun setup() {
        mockkObject(Klaviyo)
        mockkObject(DeepLinking)
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(Klaviyo)
        unmockkObject(DeepLinking)
    }

    @Test
    fun `launchIntent creates intent to open the overlay activity`() {
        val packageSlot = slot<String>()
        val classSlot = slot<String>()
        val flagsSlot = slot<Int>()
        val actionSlot = slot<String>()
        val uriSlot = slot<Uri>()
        val mockIntent = mockk<Intent>(relaxed = true).apply {
            every { setAction(any()) } returns this
            every { setData(any()) } returns this
        }
        val mockUri = mockk<Uri>()

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setClassName(capture(packageSlot), capture(classSlot)) } returns mockIntent
        every { anyConstructed<Intent>().setFlags(capture(flagsSlot)) } returns mockIntent
        every { anyConstructed<Intent>().setAction(capture(actionSlot)) } returns mockIntent
        every { anyConstructed<Intent>().setData(capture(uriSlot)) } returns mockIntent
        every { anyConstructed<Intent>().setPackage(BuildConfig.LIBRARY_PACKAGE_NAME) } returns mockIntent

        assertNotNull(
            KlaviyoMiddlewareActivity.makeLaunchIntent(
                mockContext,
                mockUri
            )
        )
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flagsSlot.captured)
        verify { anyConstructed<Intent>().setAction(Intent.ACTION_VIEW) }
        verify { anyConstructed<Intent>().setData(mockUri) }
        verify { anyConstructed<Intent>().setPackage(BuildConfig.LIBRARY_PACKAGE_NAME) }
    }

    @Test
    fun `onPushOpened processes deep link and launches host app`() {
        val mockIntent = mockk<Intent>(relaxed = true)
        val mockContext = mockk<KlaviyoMiddlewareActivity>(relaxed = true)
        val mockUri = mockk<Uri>()

        every { mockIntent.data } returns mockUri

        KlaviyoMiddlewareActivity.onPushOpened(mockIntent, mockContext)

        verify { Klaviyo.handlePush(mockIntent) }
        verify { DeepLinking.handleDeepLink(mockUri) }
    }

    @Test
    fun `onPushOpened launches host app when no deep link is present`() {
        val mockIntent = mockk<Intent>(relaxed = true)
        val mockContext = mockk<KlaviyoMiddlewareActivity>(relaxed = true)

        every { mockIntent.data } returns null

        KlaviyoMiddlewareActivity.onPushOpened(mockIntent, mockContext)

        verify { Klaviyo.handlePush(mockIntent) }
        verify { DeepLinking.sendLaunchIntent(mockContext, mockIntent.extras) }
    }
}
