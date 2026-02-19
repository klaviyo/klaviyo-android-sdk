package com.klaviyo.location

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.MissingModule
import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertThrows
import org.junit.Test

internal class GeofencingNoOpTest : BaseTest() {
    @Test
    fun `registerGeofencing throws MissingModule when provider not registered`() {
        assertThrows(MissingModule::class.java) {
            Klaviyo.registerGeofencing()
        }
    }

    @Test
    fun `unregisterGeofencing throws MissingModule when provider not registered`() {
        assertThrows(MissingModule::class.java) {
            Klaviyo.unregisterGeofencing()
        }
    }
}
