package com.klaviyo.forms.presentation

import android.content.Intent
import com.klaviyo.core.BuildConfig
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KlaviyoFormsOverlayActivityTest : BaseTest() {
    @Test
    fun `launchIntent creates intent to open the overlay activity`() {
        every { mockContext.packageName } returns BuildConfig.LIBRARY_PACKAGE_NAME
        val packageSlot = slot<String>()
        val classSlot = slot<String>()
        val flagsSlot = slot<Int>()
        val mockIntent = mockk<Intent>()

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setClassName(capture(packageSlot), capture(classSlot)) } returns mockIntent
        every { anyConstructed<Intent>().setFlags(capture(flagsSlot)) } returns mockIntent

        assertNotNull(KlaviyoFormsOverlayActivity.launchIntent)
        assertEquals(BuildConfig.LIBRARY_PACKAGE_NAME, packageSlot.captured)
        assertEquals(
            "com.klaviyo.forms.presentation.KlaviyoFormsOverlayActivity",
            classSlot.captured
        )
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flagsSlot.captured)
    }
}
