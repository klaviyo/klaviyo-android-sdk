package com.klaviyo.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.klaviyo.sample.ui.theme.KlaviyoAndroidSdkTheme

@Composable
fun SampleView(
    externalId: MutableState<String>,
    email: MutableState<String>,
    phoneNumber: MutableState<String>,
    pushToken: MutableState<String>,
    hasNotificationPermission: MutableState<Boolean>,
    setProfile: () -> Unit = {},
    resetProfile: () -> Unit = {},
    createTestEvent: () -> Unit = {},
    createViewedProductEvent: () -> Unit = {},
    registerForInAppForms: () -> Unit = {},
    unregisterFromInAppForms: () -> Unit = {},
    requestPermission: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
    ) {
        val focusManager = LocalFocusManager.current
        val keyboardActions = KeyboardActions(
            onNext = {
                // Select next input with "next" action
                focusManager.moveFocus(FocusDirection.Down)
            },
            onSend = {
                setProfile()
                focusManager.clearFocus()
            }
        )

        ViewRow(horizontalArrangement = Arrangement.Start) {
            Text(text = "Set Profile Information", style = MaterialTheme.typography.titleSmall)
        }
        ViewRow {
            OutlinedTextField(
                label = { Text("External ID") },
                value = externalId.value,
                onValueChange = { externalId.value = it },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Ascii),
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f, fill = true)
            )
        }
        ViewRow {
            OutlinedTextField(
                label = { Text("Email") },
                value = email.value,
                onValueChange = { email.value = it },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Email),
                keyboardActions = keyboardActions, modifier = Modifier.weight(1f, fill = true)
            )
        }
        ViewRow {
            OutlinedTextField(
                label = { Text("Phone Number") },
                value = phoneNumber.value,
                onValueChange = { phoneNumber.value = it },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, keyboardType = KeyboardType.Phone),
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f, fill = true)
            )
        }
        ViewRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                shape = CircleShape,
                onClick = setProfile,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Set Profile")
            }

            OutlinedButton(
                shape = CircleShape,
                onClick = resetProfile,
                enabled = externalId.value.isNotEmpty() || email.value.isNotEmpty() || phoneNumber.value.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Reset Profile")
            }
        }

        ViewRow { Divider() }

        ViewRow(horizontalArrangement = Arrangement.Start) {
            Text(text = "Create Events", style = MaterialTheme.typography.titleSmall)
        }
        ViewRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                shape = CircleShape,
                onClick = createTestEvent,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Test Event"
                )
                Text(text = "Create Test Event")
            }
            OutlinedButton(
                shape = CircleShape,
                onClick = createViewedProductEvent,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Create Test Event"
                )
                Text(text = "Viewed Product")
            }
        }

        ViewRow { Divider() }

        ViewRow(horizontalArrangement = Arrangement.Start) {
            Text(text = "In-App Forms", style = MaterialTheme.typography.titleSmall)
        }
        ViewRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                shape = CircleShape,
                onClick = registerForInAppForms,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Register")
            }
            OutlinedButton(
                shape = CircleShape,
                onClick = unregisterFromInAppForms,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Unregister")
            }
        }

        ViewRow { Divider() }

        ViewRow(horizontalArrangement = Arrangement.Start) {
            Text(text = "Push Notifications", style = MaterialTheme.typography.titleSmall)
        }
        ViewRow() {
            if (hasNotificationPermission.value) {
                Text(text = "Notification Permission Granted")
            } else {
                OutlinedButton(
                    shape = CircleShape,
                    onClick = requestPermission,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Request Notification Permission")
                }
            }
        }
        ViewRow(horizontalArrangement = Arrangement.Start) {
            Text(text = "Push Token", style = MaterialTheme.typography.labelMedium)
        }
        ViewRow() {
            Text(text = pushToken.value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ViewRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp).then(modifier),
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun SamplePreviewEmpty() {
    KlaviyoAndroidSdkTheme {
        SampleView(
            remember { mutableStateOf("") },
            remember { mutableStateOf("") },
            remember { mutableStateOf("") },
            remember { mutableStateOf("") },
            remember { mutableStateOf(false) },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SamplePreviewFilled() {
    KlaviyoAndroidSdkTheme {
        SampleView(
            remember { mutableStateOf("ABC123") },
            remember { mutableStateOf("profile@test.com") },
            remember { mutableStateOf("+1234567890") },
            remember { mutableStateOf("abcdefghijklmnopqrstuvwxyz1234567890") },
            remember { mutableStateOf(false) },
        )
    }
}
