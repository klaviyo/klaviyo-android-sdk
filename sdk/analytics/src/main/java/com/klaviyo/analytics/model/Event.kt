package com.klaviyo.analytics.model

import java.io.Serializable

/**
 * Controls the data that can be input into a map of event attributes recognised by Klaviyo
 */
class Event(val type: MetricName, properties: Map<EventKey, Serializable>?) :
    BaseModel<EventKey, Event>(properties) {

    constructor(type: String, properties: Map<EventKey, Serializable>?) : this(
        MetricName.CUSTOM(type),
        properties
    )

    constructor(type: MetricName) : this(type, null)

    constructor(type: String) : this(type, null)

    fun setValue(value: String?) = apply { this.value = value }
    var value: String?
        get() = this[EventKey.VALUE]?.toString()
        set(value) {
            this[EventKey.VALUE] = value
        }

    override fun setProperty(key: EventKey, value: Serializable?) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable?) = apply {
        this[EventKey.CUSTOM(key)] = value
    }
}
