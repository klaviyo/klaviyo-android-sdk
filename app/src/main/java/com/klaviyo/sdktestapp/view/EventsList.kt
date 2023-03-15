package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.klaviyo.sdktestapp.viewmodel.Event
import java.net.URL

@Composable
fun EventsList(
    events: List<Event>,
    modifier: Modifier = Modifier,
    onEventClick: (Event) -> Unit,
) {
    if (events.isEmpty()) {
        Surface {
            Text(text = "None")
        }
    } else {
        LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            itemsIndexed(
                events,
                key = { _, event -> event.id }
            ) { _, event ->
                EventCard(event) { onEventClick(event) }
            }
        }
    }
}

@Preview(group = "Events", showBackground = true, backgroundColor = 0xFFF0EAE2)
@Composable
private fun PreviewEvents() {
    val events = List(5) {
        Event(
            id = "$it",
            type = "Event $it",
            url = URL("https://preview.com"),
        )
    }
    EventsList(events) {}
}

@Preview(group = "Events", showBackground = true, backgroundColor = 0xFFF0EAE2)
@Composable
private fun PreviewEmptyEvents() {
    EventsList(emptyList()) {}
}
