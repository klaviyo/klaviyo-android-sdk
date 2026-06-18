package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertThrows
import org.junit.Test

internal class InAppFormsNoOpTest : BaseTest() {
    @Test
    fun `registerForInAppForms throws when provider not registered`() {
        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.registerForInAppForms()
        }
    }

    @Test
    fun `unregisterFromInAppForms throws when provider not registered`() {
        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.unregisterFromInAppForms()
        }
    }
}
