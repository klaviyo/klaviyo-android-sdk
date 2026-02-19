package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Test

internal class InAppFormsNoOpTest : BaseTest() {
    @Test
    fun `registerForInAppForms logs error when provider not registered`() {
        val result = Klaviyo.registerForInAppForms()
        assertSame(Klaviyo, result)
        verify { spyLog.error(match { it.contains("forms module not installed") }, any()) }
    }

    @Test
    fun `unregisterFromInAppForms logs error when provider not registered`() {
        val result = Klaviyo.unregisterFromInAppForms()
        assertSame(Klaviyo, result)
        verify { spyLog.error(match { it.contains("forms module not installed") }, any()) }
    }

    @Test
    fun `reInitializeInAppForms logs error when provider not registered`() {
        val result = Klaviyo.reInitializeInAppForms()
        assertSame(Klaviyo, result)
        verify { spyLog.error(match { it.contains("forms module not installed") }, any()) }
    }
}
