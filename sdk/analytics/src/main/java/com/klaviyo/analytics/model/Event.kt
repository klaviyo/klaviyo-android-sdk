package com.klaviyo.analytics.model

import java.io.Serializable

/**
 * Controls the data that can be input into a map of event attributes recognised by Klaviyo
 */
class Event(val type: EventMetric, properties: Map<EventKey, Serializable>?) :
    BaseModel<EventKey, Event>(properties) {

    constructor(metric: EventMetric) : this(metric, null)

    @Deprecated(
        "Use Event constructor with EventMetric instead. See migration guide for details.",
        ReplaceWith("Event(metric)")
    )
    constructor(type: EventType) : this(type, null)

    constructor(type: String) : this(type, null)

    constructor(type: String, properties: Map<EventKey, Serializable>?) : this(
        EventMetric.CUSTOM(type),
        properties
    )

    fun setValue(value: Double?) = apply { this.value = value }
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
