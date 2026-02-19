package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingKlaviyoModule
import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertThrows
import org.junit.Test

internal class GeofencingNoOpTest : BaseTest() {
    @Test
    fun `registerGeofencing throws when provider not registered`() {
        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.registerGeofencing()
        }
    }

    @Test
    fun `unregisterGeofencing throws when provider not registered`() {
        assertThrows(MissingKlaviyoModule::class.java) {
            Klaviyo.unregisterGeofencing()
        }
    }
}
