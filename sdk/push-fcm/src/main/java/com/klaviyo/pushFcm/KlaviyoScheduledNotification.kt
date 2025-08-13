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
    ) {
        val scheduledTimeMillis: Long get() = scheduledTime
    }

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

            // Add to list of all notifications
            addToAllNotifications(tag)

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
        removeFromAllNotifications(tag)
        Registry.log.verbose("Removed scheduled notification with tag: $tag")
    }

    /**
     * Get the storage key for a notification tag
     */
    private fun getStorageKey(tag: String) = "$STORAGE_KEY_PREFIX$tag"

    // Key to store all notification tags
    private const val ALL_NOTIFICATIONS_KEY = "klaviyo_all_scheduled_notifications"

    /**
     * Get all stored scheduled notifications
     * * @return A map of notification tags to their associated data
     */
    fun getAllNotifications(): Map<String, NotificationData> {
        val result = mutableMapOf<String, NotificationData>()

        try {
            // Get all notification tags from storage
            val allTags = Registry.dataStore.fetch(ALL_NOTIFICATIONS_KEY)?.split(",") ?: emptyList()

            allTags.forEach { tag ->
                // Get the notification data
                val notification = getNotification(tag)
                if (notification != null) {
                    result[tag] = notification
                }
            }
        } catch (e: Exception) {
            Registry.log.warning("Failed to retrieve all scheduled notifications", e)
        }

        return result
    }

    /**
     * Update the list of all notification tags when storing a new notification
     * * @param tag Notification tag to add
     */
    private fun addToAllNotifications(tag: String) {
        val existingTags = Registry.dataStore.fetch(ALL_NOTIFICATIONS_KEY)?.split(",")?.toMutableList() ?: mutableListOf()

        if (!existingTags.contains(tag)) {
            existingTags.add(tag)
            Registry.dataStore.store(ALL_NOTIFICATIONS_KEY, existingTags.joinToString(","))
        }
    }

    /**
     * Update the list of all notification tags when removing a notification
     * * @param tag Notification tag to remove
     */
    private fun removeFromAllNotifications(tag: String) {
        val existingTags = Registry.dataStore.fetch(ALL_NOTIFICATIONS_KEY)?.split(",")?.toMutableList() ?: mutableListOf()

        if (existingTags.contains(tag)) {
            existingTags.remove(tag)
            Registry.dataStore.store(ALL_NOTIFICATIONS_KEY, existingTags.joinToString(","))
        }
    }
}
