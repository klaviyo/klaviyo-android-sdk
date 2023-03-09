package com.klaviyo.sdktestapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

@Composable
private fun PushTokenView(
    pushToken: String = "",
    onTokenCopied: () -> Unit = {},
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
    ) {
        val (label, tokenField, copyButton) = createRefs()
        Text(
            text = "Push Token",
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.constrainAs(label) {
                top.linkTo(parent.top)
                bottom.linkTo(tokenField.top)
                start.linkTo(parent.start)
            },
            fontSize = 12.sp,
        )
        Text(
            text = pushToken.ifEmpty { "No Push Token" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .selectable(selected = false, enabled = false, null) {}
                .constrainAs(tokenField) {
                    top.linkTo(label.bottom)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(copyButton.start, 16.dp)
                    width = Dimension.fillToConstraints
                },
        )
        Button(
            enabled = pushToken.isNotBlank(),
            onClick = onTokenCopied,
            elevation = ButtonDefaults.elevation(0.dp),
            shape = CircleShape,
            modifier = Modifier.constrainAs(copyButton) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                end.linkTo(parent.end)
            },
        ) {
            Text(
                text = "Copy",
            )
        }
    }
}

@Preview(group = "PushTokenView")
@Composable
private fun EmptyPushToken() {
    PushTokenView(
        pushToken = ""
    )
}

@Preview(group = "PushTokenView")
@Composable
private fun HasPushToken() {
    PushTokenView(
        pushToken = "onac784y5oa9283n569a285c6pa9283cwa9v38v5nap93w86v5p"
    )
}

@Composable
fun PushSettings(
    isPushEnabled: Boolean = false,
    pushToken: String = "",
    onRequestedPushNotification: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onCopyPushToken: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Push Notifications",
                )
                Box(modifier = Modifier.weight(1f, fill = true))
                Button(
                    enabled = !isPushEnabled,
                    onClick = onRequestedPushNotification,
                    elevation = ButtonDefaults.elevation(0.dp),
                    shape = CircleShape,
                ) {
                    Text(
                        text = if (isPushEnabled) "Enabled" else "Enable",
                    )
                }
            }
            Divider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .clickable(
                        onClick = onOpenNotificationSettings,
                        enabled = true,
                        interactionSource = interactionSource,
                        indication = rememberRipple(bounded = true),
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = "Notification Settings",
                )
                Box(modifier = Modifier.weight(1f, fill = true))
                Icon(
                    imageVector = Icons.Default.ArrowRight,
                    contentDescription = "Open notification settings",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PushTokenView(
                pushToken = pushToken,
                onTokenCopied = onCopyPushToken,
            )
        }
    }
}

@Preview(group = "PushSettings", showSystemUi = true)
@Composable
private fun DisabledPushSettings() {
    PushSettings(
        isPushEnabled = false,
        pushToken = "alkj4h2to9s87rglknaucy490w37tnv0w3857cscwo87n5syos857nycoyoli3yhsj",
    )
}

@Preview(group = "PushSettings")
@Composable
private fun EnabledPushSettings() {
    PushSettings(
        isPushEnabled = true,
        pushToken = "alkj4h2to9s87rglknaucy490w37tnv0w3857cscwo87n5syos857nycoyoli3yhsj",
    )
}
