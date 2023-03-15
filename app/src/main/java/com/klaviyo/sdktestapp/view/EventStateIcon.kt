package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.SwapHorizontalCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.klaviyo.sdktestapp.viewmodel.Event

@Composable
fun EventStateIcon(eventState: Event.State, modifier: Modifier = Modifier) {
    Icon(
        imageVector = when (eventState) {
            Event.State.Queued -> Icons.Default.Pending
            Event.State.Pending -> Icons.Default.SwapHorizontalCircle
            Event.State.Retrying -> Icons.Default.Error
            Event.State.Failed -> Icons.Default.Cancel
            Event.State.Complete -> Icons.Default.CheckCircle
        },
        tint = when (eventState) {
            Event.State.Queued -> Color.InfoBlue
            Event.State.Pending -> Color.NoticeYellow
            Event.State.Retrying -> Color.WarningOrange
            Event.State.Failed -> Color.ErrorRed
            Event.State.Complete -> Color.SuccessGreen
        },
        contentDescription = "Request ${eventState.name}",
        modifier = modifier.then(Modifier.size(32.dp))
    )
}
