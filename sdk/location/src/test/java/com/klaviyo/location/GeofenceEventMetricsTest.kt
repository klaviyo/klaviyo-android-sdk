package com.klaviyo.location

import com.klaviyo.fixtures.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Test

internal class GeofenceEventMetricsTest : BaseTest() {

    @Test
    fun `Keys have correct names`() {
        assertEquals("\$geofence_enter", GeofenceEventMetric.ENTER.name)
        assertEquals("\$geofence_exit", GeofenceEventMetric.EXIT.name)
        assertEquals("\$geofence_dwell", GeofenceEventMetric.DWELL.name)
        assertEquals("\$geofence_id", GeofenceEventProperty.GEOFENCE_ID.name)
    }
}
