package com.klaviyo.analytics.model

import java.io.Serializable

/**
 * Controls the data that can be input into a map of event attributes recognised by Klaviyo
 */
class Event(val type: EventType, properties: Map<EventKey, Serializable>?) :
    BaseModel<EventKey, Event>(properties) {

    constructor(type: String, properties: Map<EventKey, Serializable>?) : this(
        EventType.CUSTOM(type),
        properties
    )

    constructor(type: EventType) : this(type, null)

    constructor(type: String) : this(type, null)

    fun setValue(value: Double) = apply { this.value = value }
    var value: Double?
        get() = when (val value = this[EventKey.VALUE]) {
            is Double -> value
            else -> try {
                value.toString().toDouble()
            } catch (e: NumberFormatException) {
                null
            }
        }
        set(value) {
            this[EventKey.VALUE] = value
        }

    override fun setProperty(key: EventKey, value: Serializable?) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable?) =
        setProperty(EventKey.CUSTOM(key), value)
}
