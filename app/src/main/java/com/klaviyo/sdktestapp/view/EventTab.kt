package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.klaviyo.sdktestapp.viewmodel.Event
import com.klaviyo.sdktestapp.viewmodel.NavigationState
import java.net.URL

@Composable
fun EventsPage(
    events: List<Event>,
    selectedEvent: Event?,
    onClearClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    onEventClick: (Event?) -> Unit,
    onNavigate: (NavigationState) -> Unit,
) {
    if (selectedEvent is Event) {
        // Set tab bar content for detail view
        onNavigate(
            NavigationState(
                title = selectedEvent.type,
                navAction = NavigationState.makeBackButton { onEventClick(null) },
                NavigationState.Action(
                    imageVector = { Icons.Default.CopyAll },
                    contentDescription = "Copy",
                    onCopyClicked
                )
            )
        )

        // Render event detail composable
        EventDetail(event = selectedEvent)
    } else {
        // Set tab bar content for list view
        onNavigate(
            NavigationState(
                title = "Events",
                navAction = null,
                NavigationState.Action(
                    imageVector = { Icons.Default.DeleteSweep },
                    contentDescription = "Clear List",
                    onClearClicked
                )
            )
        )

        // Render events list composable
        EventsList(
            events,
            onEventClick = onEventClick
        )
    }
}

@Preview(group = "Events", showBackground = true, backgroundColor = 0xFFF0EAE2)
@Composable
fun PreviewEventsTab() {
    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var navigationState by remember { mutableStateOf(NavigationState("Events")) }
    val events = remember {
        mutableStateListOf(
            *Array(5) {
                Event(
                    id = "$it",
                    type = "Event $it",
                    url = URL("https://preview.com/test/$it"),
                    state = when {
                        it < 2 -> Event.State.Complete
                        it == 2 -> Event.State.Pending
                        else -> Event.State.Queued
                    }
                )
            }
        )
    }

    Column {
        TopBar(navState = navigationState)
        EventsPage(
            events = events,
            selectedEvent = selectedEvent,
            onClearClicked = { events.clear() },
            onCopyClicked = { /* */ },
            onEventClick = { selectedEvent = it },
            onNavigate = { navigationState = it },
        )
    }
}
