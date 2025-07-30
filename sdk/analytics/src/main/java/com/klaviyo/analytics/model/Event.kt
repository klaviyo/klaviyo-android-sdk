package com.klaviyo.analytics.model

import java.io.Serializable

/**
 * Controls the data that can be input into a map of event attributes recognised by Klaviyo
 */
class Event(val metric: EventMetric, properties: Map<EventKey, Serializable>?) :
    BaseModel<EventKey, Event>(properties) {

    constructor(metric: String, properties: Map<EventKey, Serializable>?) : this(
        EventMetric.CUSTOM(metric),
        properties
    )

    constructor(metric: EventMetric) : this(metric, null)

    constructor(metric: String) : this(metric, null)

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

    override fun copy(): Event = Event(metric).merge(this)

    override fun merge(other: Event?) = apply { super.merge(other) }
}
