package com.klaviyo.forms.presentation

import com.klaviyo.core.BuildConfig
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.MockIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class KlaviyoFormsOverlayActivityTest : BaseTest() {
    @Test
    fun `launchIntent creates intent to open the overlay activity`() {
        val mockIntent = MockIntent.setupIntentMocking()

        assertNotNull(KlaviyoFormsOverlayActivity.launchIntent)
        assertEquals(BuildConfig.LIBRARY_PACKAGE_NAME, mockIntent.packageName.captured)
        assertEquals(
            "com.klaviyo.forms.presentation.KlaviyoFormsOverlayActivity",
            mockIntent.className.captured
        )
        // Form intent
        assertFalse(null, mockIntent.flags.isCaptured)
    }
}
