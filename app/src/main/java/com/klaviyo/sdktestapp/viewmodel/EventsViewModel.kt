package com.klaviyo.sdktestapp.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.sdktestapp.services.Clipboard

/**
 * Observes API request events from the SDK
 * and projects an observable list of unique API calls
 *
 * @constructor
 */
class EventsViewModel(private val context: Context) {

    private companion object {
        // I'd be shocked if you hit this, just want to keep from overflowing
        const val MAX_ITEMS = 100
    }

    data class ViewState(
        val events: List<Event>,
    )

    private val _events = LinkedHashMap<String, Event>()

    var viewState by mutableStateOf(snapshotState())
        private set

    var detailEvent: Event? by mutableStateOf(null)
        private set

    init {
        observeSdk()
    }

    /**
     * Register a callback for all of the SDK's API requests state changes.
     */
    private fun observeSdk() = Registry.get<ApiClient>().onApiRequest { request ->
        _events[request.uuid] = Event(request)

        if (_events.count() > MAX_ITEMS) {
            _events.firstNotNullOf { _events.remove(it.key) }
        }

        if (detailEvent?.id == request.uuid) {
            detailEvent = _events[request.uuid]
        }

        refreshState()
    }

    fun createEvent() {
        Klaviyo.createEvent(EventType.CUSTOM("Test Event"))
    }

    fun selectEvent(event: Event? = null) {
        detailEvent = event
    }

    fun copyEvent() {
        detailEvent?.let { event ->
            Clipboard(context).logAndCopy("ApiRequest", event.toString(2))
        }
    }

    private fun snapshotState() = ViewState(_events.values.toList())

    private fun refreshState() {
        viewState = snapshotState()
    }

    fun clearEvents() {
        _events.clear()
        refreshState()
    }
}
