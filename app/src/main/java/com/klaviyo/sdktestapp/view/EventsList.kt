package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        Card(
            modifier = modifier.then(
                Modifier.fillMaxWidth()
                    .padding(8.dp)
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.ListAlt,
                    contentDescription = "Empty List",
                    Modifier.size(50.dp)
                )
                Text(text = "No recent events", style = MaterialTheme.typography.h6)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.then(Modifier.fillMaxWidth()),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
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
