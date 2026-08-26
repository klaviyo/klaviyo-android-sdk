package com.klaviyo.analytics.model

import com.klaviyo.core.Registry
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
                value?.toString()?.toDouble()
            } catch (e: NumberFormatException) {
                Registry.log.error("Event value is not a number: $value", e)
                null
            }
        }

        @JvmSynthetic
        set(value) {
            this[EventKey.VALUE] = value
        }

    /**
     * ISO 4217 currency code for [value], e.g. "USD". The API rejects a code that is not valid
     * ISO 4217 with a 400 and does not ingest the event.
     */
    fun setValueCurrency(valueCurrency: String?) = apply { this.valueCurrency = valueCurrency }
    var valueCurrency: String?
        get() = this[EventKey.VALUE_CURRENCY]?.toString()

        @JvmSynthetic
        set(value) {
            this[EventKey.VALUE_CURRENCY] = value
        }

    fun setUniqueId(uniqueId: String?) = apply { this.uniqueId = uniqueId }
    var uniqueId: String?
        get() = this[EventKey.EVENT_ID]?.toString()

        @JvmSynthetic
        set(value) {
            this[EventKey.EVENT_ID] = value
        }

    override fun setProperty(key: EventKey, value: Serializable?) = apply {
        this[key] = value
    }

    override fun setProperty(key: String, value: Serializable?) =
        setProperty(EventKey.CUSTOM(key), value)

    override fun copy(): Event = Event(metric).merge(this)

    override fun merge(other: Event?) = apply { super.merge(other) }
}
