package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingModule
import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertThrows
import org.junit.Test

internal class InAppFormsNoOpTest : BaseTest() {
    @Test
    fun `registerForInAppForms throws MissingModule when provider not registered`() {
        assertThrows(MissingModule::class.java) {
            Klaviyo.registerForInAppForms()
        }
    }

    @Test
    fun `unregisterFromInAppForms throws MissingModule when provider not registered`() {
        assertThrows(MissingModule::class.java) {
            Klaviyo.unregisterFromInAppForms()
        }
    }

    @Test
    fun `reInitializeInAppForms throws MissingModule when provider not registered`() {
        assertThrows(MissingModule::class.java) {
            Klaviyo.reInitializeInAppForms()
        }
    }
}
