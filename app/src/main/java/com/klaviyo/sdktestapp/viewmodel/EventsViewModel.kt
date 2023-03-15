package com.klaviyo.sdktestapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.EventType
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry

/**
 * Observes API request events from the SDK
 * and projects an observable list of unique API calls
 *
 * @constructor
 */
class EventsViewModel {

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

    init {
        Registry.get<ApiClient>().onApiRequest { request ->
            _events[request.uuid] = Event(request)

            if (_events.count() > MAX_ITEMS) {
                _events.firstNotNullOf { _events.remove(it.key) }
            }

            refreshState()
        }
    }

    fun createEvent() {
        Klaviyo.createEvent(EventType.CUSTOM("Test Event"))
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
