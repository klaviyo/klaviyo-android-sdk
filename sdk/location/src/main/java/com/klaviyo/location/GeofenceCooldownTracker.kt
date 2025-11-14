package com.klaviyo.location

import com.klaviyo.core.Registry
import org.json.JSONObject

/**
 * Manages geofence transition cooldown periods to prevent duplicate events from GPS drift.
 *
 * GPS accuracy typically fluctuates 10-50m, causing rapid enter/exit events when users
 * linger near geofence boundaries. This tracker enforces a cooldown period per geofence
 * and transition type to filter out noise while allowing legitimate events.
 *
 * Cooldown data is stored as a JSON map in DataStore with automatic cleanup of stale entries.
 */
internal class GeofenceCooldownTracker {
    companion object {
        /**
         * Key for storing geofence transition cooldown timestamps as a map
         */
        private const val GEOFENCE_COOLDOWNS_KEY = "geofence_cooldowns"

        /**
         * Cooldown period for geofence transitions in milliseconds (60 seconds)
         * Prevents duplicate events from GPS drift
         */
        private const val GEOFENCE_TRANSITION_COOLDOWN = 60_000L
    }

    /**
     * Externally trigger the saved cooldown map to expire old keys
     */
    fun clean() {
        loadCooldownMap()
    }

    /**
     * Check if a geofence transition is allowed (not in cooldown period)
     *
     * @param geofenceId The geofence ID to check
     * @param transition The transition type to check
     * @return true if the event should be created (cooldown elapsed or no previous event), false otherwise
     */
    fun isAllowed(geofenceId: String, transition: KlaviyoGeofenceTransition): Boolean {
        val cooldownMap = loadCooldownMap()
        val mapKey = getCooldownMapKey(geofenceId, transition)
        val lastTransitionTime = cooldownMap[mapKey] ?: return true

        val currentTime = Registry.clock.currentTimeMillis()
        val elapsedTime = currentTime - lastTransitionTime

        return if (elapsedTime < GEOFENCE_TRANSITION_COOLDOWN) {
            val remainingTime = (GEOFENCE_TRANSITION_COOLDOWN - elapsedTime) / 1000
            Registry.log.verbose(
                "Suppressed geofence ${transition.name.lowercase()} event for $geofenceId - ${remainingTime}s remaining in cooldown"
            )
            false
        } else {
            Registry.log.debug(
                "Allowing geofence ${transition.name.lowercase()} event for $geofenceId - ${elapsedTime / 1000}s since last event"
            )
            true
        }
    }

    /**
     * Record the timestamp of a geofence transition event for cooldown tracking
     *
     * @param geofenceId The geofence ID
     * @param transition The transition type
     */
    fun recordTransition(geofenceId: String, transition: KlaviyoGeofenceTransition) {
        val cooldownMap = loadCooldownMap().toMutableMap()
        val mapKey = getCooldownMapKey(geofenceId, transition)
        val currentTime = Registry.clock.currentTimeMillis()

        cooldownMap[mapKey] = currentTime
        saveCooldownMap(cooldownMap)

        Registry.log.verbose("Recorded transition time for $geofenceId ${transition.name}")
    }

    /**
     * Load the cooldown map from DataStore and clean up stale entries
     *
     * @return Map of geofence+transition keys to timestamps
     */
    private fun loadCooldownMap(): Map<String, Long> {
        val storedJson = Registry.dataStore.fetch(GEOFENCE_COOLDOWNS_KEY) ?: return emptyMap()
        val currentTime = Registry.clock.currentTimeMillis()

        return try {
            JSONObject(storedJson).let { json ->
                json.keys().asSequence()
                    .associateWith { json.getLong(it) }
                    .filterValues { timestamp ->
                        // Cleanup: keep only entries within cooldown period
                        currentTime - timestamp <= GEOFENCE_TRANSITION_COOLDOWN
                    }.also { cleanedList ->
                        // If we filtered out expired keys, commit the update back to disk
                        if (cleanedList.size != json.length()) {
                            saveCooldownMap(cleanedList)
                        }
                    }
            }
        } catch (e: Exception) {
            Registry.log.error("Failed to load geofence cooldowns", e)
            emptyMap()
        }
    }

    /**
     * Save the cooldown map to DataStore
     *
     * @param cooldownMap Map of geofence+transition keys to timestamps
     */
    private fun saveCooldownMap(cooldownMap: Map<String, Long>) {
        try {
            val json = JSONObject(cooldownMap)
            Registry.dataStore.store(GEOFENCE_COOLDOWNS_KEY, json.toString())
        } catch (e: Exception) {
            Registry.log.error("Failed to save geofence cooldowns", e)
        }
    }

    /**
     * Generate the map key for a geofence transition
     *
     * @param geofenceId The geofence ID
     * @param transition The transition type
     * @return The map key string
     */
    private fun getCooldownMapKey(geofenceId: String, transition: KlaviyoGeofenceTransition): String =
        "$geofenceId:${transition.name}"
}
