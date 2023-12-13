package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.layout.Column
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.klaviyo.analytics.model.EventType
import com.klaviyo.sdktestapp.viewmodel.Event
import com.klaviyo.sdktestapp.viewmodel.NavigationState
import com.klaviyo.sdktestapp.viewmodel.TabIndex
import java.net.URL

@Composable
fun EventsPage(
    events: List<Event>,
    selectedEvent: Event?,
    onCreateEvent: (EventType) -> Unit,
    onClearClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    onEventClick: (Event?) -> Unit,
    onNavigate: (NavigationState) -> Unit,
) {
    if (selectedEvent is Event) {
        // Set tab bar content for detail view
        onNavigate(
            NavigationState(
                TabIndex.Events,
                title = selectedEvent.type,
                navAction = NavigationState.makeBackButton { onEventClick(null) },
                floatingAction = null,
                NavigationState.Action(
                    imageVector = { Icons.Default.CopyAll },
                    contentDescription = "Copy",
                    onClick = onCopyClicked
                )
            )
        )

        // Render event detail composable
        EventDetail(event = selectedEvent)
    } else {
        // Set tab bar content for list view
        onNavigate(
            NavigationState(
                TabIndex.Events,
                title = "Events",
                navAction = null,
                floatingAction = NavigationState.Action(
                    imageVector = { Icons.Default.Add },
                    contentDescription = "Add Event",
                    subActions = listOf(
                        NavigationState.Action(
                            imageVector = { Icons.Filled.Science },
                            contentDescription = "Test Event",
                        ) {
                            onCreateEvent(EventType.CUSTOM("Test Event"))
                        },
                        NavigationState.Action(
                            imageVector = { Icons.Filled.RemoveRedEye },
                            contentDescription = "Viewed Product",
                        ) {
                            onCreateEvent(EventType.VIEWED_PRODUCT)
                        },
                        NavigationState.Action(
                            imageVector = { Icons.Filled.Search },
                            contentDescription = "Searched Products",
                        ) {
                            onCreateEvent(EventType.SEARCHED_PRODUCTS)
                        }
                    )
                ) { },
                NavigationState.Action(
                    imageVector = { Icons.Default.DeleteSweep },
                    contentDescription = "Clear List",
                    onClick = onClearClicked
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
    fun createPreviewEvent(i: Int) = Event(
        id = "$i",
        type = "Event $i",
        url = URL("https://preview.com/test/$i"),
        state = when {
            i < 2 -> Event.State.Complete
            i == 2 -> Event.State.Pending
            else -> Event.State.Queued
        }
    )

    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var navigationState by remember { mutableStateOf(NavigationState(TabIndex.Events, "Events")) }
    val events = remember { mutableStateListOf(*Array(5) { createPreviewEvent(it) }) }
    var i by remember { mutableStateOf(5) }

    Column {
        TopBar(navState = navigationState)
        EventsPage(
            events = events,
            selectedEvent = selectedEvent,
            onCreateEvent = { events.add(createPreviewEvent(i++)) },
            onClearClicked = { events.clear() },
            onCopyClicked = { /* */ },
            onEventClick = { selectedEvent = it },
            onNavigate = { navigationState = it },
        )

        navigationState.floatingAction?.let { action ->
            FloatingActionButton(
                backgroundColor = MaterialTheme.colors.primarySurface,
                onClick = action.onClick
            ) {
                Icon(
                    imageVector = action.imageVector(),
                    contentDescription = action.contentDescription
                )
            }
        }
    }
}
