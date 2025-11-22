package com.klaviyo.location

import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry

/**
 * Observes company ID changes and refreshes geofences when the company changes.
 *
 * When the company ID changes:
 * 1. Stops monitoring current geofences
 * 2. Clears stored geofences (they belong to the old company)
 * 3. Fetches and monitors new geofences for the new company
 */
internal class CompanyObserver() : StateChangeObserver {

    private var isObserving = false

    fun startObserver() {
        if (!isObserving) {
            Registry.get<State>().onStateChange(this)
            isObserving = true
        }
    }

    fun stopObserver() {
        if (isObserving) {
            Registry.get<State>().offStateChange(this)
            isObserving = false
        }
    }

    override fun invoke(change: StateChange) {
        when (change) {
            is StateChange.ApiKey -> {
                Registry.log.debug(
                    "Company ID changed from ${change.oldValue} to ${Registry.get<State>().apiKey}, refreshing geofences"
                )

                // Stop old geofences immediately, then restart monitoring with new company
                Registry.getOrNull<LocationManager>()?.run {
                    stopGeofenceMonitoring()
                    clearStoredGeofences()
                    startGeofenceMonitoring()
                }
            }

            else -> Unit
        }
    }
}
