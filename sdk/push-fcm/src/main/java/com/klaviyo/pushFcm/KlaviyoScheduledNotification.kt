package com.klaviyo.pushFcm

import com.klaviyo.core.Registry
import java.util.Date
import org.json.JSONException
import org.json.JSONObject

/**
 * Class to handle storage and retrieval of scheduled notifications
 */
object KlaviyoScheduledNotification {
    private const val STORAGE_KEY_PREFIX = "klaviyo_scheduled_notification_"

    /**
     * Data class representing the minimal data needed to recreate a notification
     */
    data class NotificationData(
        val tag: String,
        val data: Map<String, String>,
        val scheduledTime: Long
    )

    /**
     * Store a notification for scheduled delivery
     *
     * @param tag Unique identifier for the notification
     * @param data RemoteMessage data map to store
     * @param scheduledTime Time when the notification should be displayed
     * @return Whether the notification was successfully stored
     */
    fun storeNotification(tag: String, data: Map<String, String>, scheduledTime: Long): Boolean {
        return try {
            val notificationData = NotificationData(
                tag = tag,
                data = data,
                scheduledTime = scheduledTime
            )

            // Convert to JSON for storage
            val json = JSONObject().apply {
                put("tag", notificationData.tag)
                put("scheduledTime", notificationData.scheduledTime)

                // Store the data map as a nested JSONObject
                val dataObj = JSONObject()
                notificationData.data.forEach { (key, value) ->
                    dataObj.put(key, value)
                }
                put("data", dataObj)
            }

            // Store in shared preferences
            Registry.dataStore.store(getStorageKey(tag), json.toString())

            Registry.log.info(
                "Stored scheduled notification with tag: $tag for time: ${Date(scheduledTime)}"
            )
            true
        } catch (e: Exception) {
            Registry.log.warning("Failed to store scheduled notification", e)
            false
        }
    }

    /**
     * Retrieve a stored notification by its tag
     *
     * @param tag Unique identifier for the notification
     * @return The stored notification data or null if not found
     */
    fun getNotification(tag: String): NotificationData? {
        val json = Registry.dataStore.fetch(getStorageKey(tag)) ?: return null

        return try {
            val jsonObj = JSONObject(json)

            // Extract data map from nested JSONObject
            val dataObj = jsonObj.getJSONObject("data")
            val dataMap = mutableMapOf<String, String>()
            dataObj.keys().forEach { key ->
                dataMap[key] = dataObj.getString(key)
            }

            NotificationData(
                tag = jsonObj.getString("tag"),
                scheduledTime = jsonObj.getLong("scheduledTime"),
                data = dataMap
            )
        } catch (e: JSONException) {
            Registry.log.warning("Failed to parse stored notification: $json", e)
            null
        }
    }

    /**
     * Remove a stored notification after it has been displayed or cancelled
     *
     * @param tag Unique identifier for the notification
     */
    fun removeNotification(tag: String) {
        Registry.dataStore.clear(getStorageKey(tag))
        Registry.log.verbose("Removed scheduled notification with tag: $tag")
    }

    /**
     * Get the storage key for a notification tag
     */
    private fun getStorageKey(tag: String) = "$STORAGE_KEY_PREFIX$tag"
}
