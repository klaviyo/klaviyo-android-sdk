package com.klaviyo.inbox

import androidx.room.TypeConverter
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.JSONUtil.getStringNullable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Stores the entity's nested values as JSON text columns. The JSON layout is part of the stored
 * schema, so changing it needs a migration.
 *
 * Stored text that no longer parses reads back as empty, so one bad row cannot fail a query.
 */
internal class InboxConverters {

    @TypeConverter
    fun actionsToJson(actions: List<InboxRecordAction>): String = JSONArray().apply {
        actions.forEach { put(it.toJson()) }
    }.toString()

    @TypeConverter
    fun jsonToActions(json: String): List<InboxRecordAction> = readOrEmpty(emptyList()) {
        val array = JSONArray(json)
        List(array.length()) { array.getJSONObject(it).toAction() }
    }

    @TypeConverter
    fun customDataToJson(customData: Map<String, String>): String = JSONObject(customData).toString()

    @TypeConverter
    fun jsonToCustomData(json: String): Map<String, String> = readOrEmpty(emptyMap()) {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { obj.getString(it) }
    }

    private fun InboxRecordAction.toJson() = JSONObject()
        .put(ID_KEY, id)
        .put(LABEL_KEY, label)
        .put(TYPE_KEY, destination.type)
        .put(URL_KEY, destination.url ?: JSONObject.NULL)

    private fun JSONObject.toAction() = InboxRecordAction(
        id = getString(ID_KEY),
        label = getString(LABEL_KEY),
        destination = InboxRecordDestination(getString(TYPE_KEY), getStringNullable(URL_KEY))
    )

    private inline fun <T> readOrEmpty(empty: T, read: () -> T): T = try {
        read()
    } catch (e: JSONException) {
        Registry.log.warning("Mobile Inbox could not read stored message data", e)
        empty
    }

    private companion object {
        const val ID_KEY = "id"
        const val LABEL_KEY = "label"
        const val TYPE_KEY = "type"
        const val URL_KEY = "url"
    }
}
