package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.klaviyo.sdktestapp.viewmodel.Event
import java.net.URL

@Composable
fun EventCard(
    event: Event,
    modifier: Modifier = Modifier,
    onEventClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier.then(
            Modifier.fillMaxWidth()
        ),
        color = MaterialTheme.colors.surface,
    ) {
        ConstraintLayout(
            Modifier
                .clickable(
                    onClick = { onEventClick() },
                    enabled = true,
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = true),
                )
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            val (title, description, status, chevron) = createRefs()
            Text(
                text = event.type,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.constrainAs(title) {
                    top.linkTo(parent.top)
                    bottom.linkTo(description.top, 4.dp)
                    start.linkTo(parent.start)
                }
            )
            Text(
                text = event.formatDate(event.queuedTime),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.constrainAs(description) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    width = Dimension.fillToConstraints
                }
            )
            EventStateIcon(
                event.state,
                modifier = Modifier
                    .constrainAs(status) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        end.linkTo(chevron.start, 16.dp)
                    }
            )
            Icon(
                imageVector = Icons.Default.ArrowRight,
                contentDescription = "Open event details",
                modifier = Modifier
                    .size(32.dp)
                    .constrainAs(chevron) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end)
                    }
            )
        }
    }
}

@Preview(group = "Events", showBackground = true, backgroundColor = 0xFFF0EAE2)
@Composable
private fun PreviewEventCard() {
    EventCard(
        event = Event(
            id = "",
            type = "Profile",
            url = URL("https://preview.com"),
        ),
        modifier = Modifier.padding(5.dp)
    ) { }
}
