package com.klaviyo.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.klaviyo.sample.ui.theme.KlaviyoAndroidSdkTheme
import java.text.DateFormat
import java.util.Date

/**
 * Displays the list of push notifications the sample app has received — the title, body, and any
 * custom key-value pairs — matching the data extracted in [SamplePushService]. This is the Android
 * counterpart to the iOS example app's Push Log screen.
 */
@Composable
fun PushLogView(
    entries: List<PushLogEntry>,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    KlaviyoAndroidSdkTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PushLogHeader(
                    canClear = entries.isNotEmpty(),
                    onClear = onClear,
                    onClose = onClose
                )
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                if (entries.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries, key = { it.id }) { entry ->
                            PushLogRow(entry)
                            HorizontalDivider(
                                Modifier,
                                DividerDefaults.Thickness,
                                DividerDefaults.color
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PushLogHeader(
    canClear: Boolean,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        TextButton(
            onClick = onClose,
            modifier = Modifier.semantics { testTag = SampleTestTags.BTN_CLOSE_PUSH_LOG }
        ) {
            Text("Close")
        }
        Text(
            text = "Push Log",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onClear,
            enabled = canClear,
            modifier = Modifier.semantics { testTag = SampleTestTags.BTN_CLEAR_PUSH_LOG }
        ) {
            Text("Clear")
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(0.dp))
        Text(
            text = "No Push Notifications Yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Received pushes will appear here with their title, body, and custom data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun PushLogRow(entry: PushLogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceChip(entry.source)
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(entry.receivedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = entry.title.ifEmpty { "(no title)" },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = entry.body.ifEmpty { "(no body)" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (entry.customData.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                entry.customData.toSortedMap().forEach { (key, value) ->
                    Text(
                        text = "$key: $value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: PushLogEntry.Source) {
    Text(
        text = source.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private fun formatTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(epochMillis))

@Preview(showBackground = true)
@Composable
private fun PushLogPreviewEmpty() {
    KlaviyoAndroidSdkTheme {
        PushLogView(entries = emptyList(), onClear = {}, onClose = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PushLogPreviewFilled() {
    KlaviyoAndroidSdkTheme {
        PushLogView(
            entries = listOf(
                PushLogEntry(
                    id = "1",
                    receivedAt = System.currentTimeMillis(),
                    source = PushLogEntry.Source.NOTIFICATION,
                    title = "Sale is live!",
                    body = "20% off everything today only",
                    customData = mapOf("promo_code" to "SAVE20", "screen" to "sale")
                ),
                PushLogEntry(
                    id = "2",
                    receivedAt = System.currentTimeMillis(),
                    source = PushLogEntry.Source.SILENT,
                    title = "",
                    body = "",
                    customData = mapOf("sync" to "orders")
                )
            ),
            onClear = {},
            onClose = {}
        )
    }
}
