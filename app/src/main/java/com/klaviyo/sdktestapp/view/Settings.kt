@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)

package com.klaviyo.sdktestapp.view

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.klaviyo.core.BuildConfig as SdkBuildConfig
import com.klaviyo.sdktestapp.BuildConfig as AppBuildConfig
import com.klaviyo.sdktestapp.viewmodel.SettingsViewModel

@Composable
fun Settings(
    viewState: SettingsViewModel.ViewState,
    onRequestedPushNotification: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onCopyPushToken: () -> Unit = {},
    onRequestPushToken: () -> Unit = {},
    onExpirePushToken: () -> Unit = {},
    onSendLocalNotification: () -> Unit = {},
    setBaseUrl: () -> Unit = {},
) {
    val isPushEnabled = viewState.isNotificationPermitted
    val pushToken = viewState.pushToken
    val settingsLinkInteractionSource = remember { MutableInteractionSource() }
    val serverSetInteractionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val focusRequester = FocusRequester()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        FormRow {
            Text(text = "Push Notifications")
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
        FormRow(
            modifier = Modifier.clickable(
                onClick = onOpenNotificationSettings,
                enabled = true,
                interactionSource = settingsLinkInteractionSource,
                indication = rememberRipple(bounded = true),
            )
        ) {
            Text(text = "Notification Settings")
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
        CopyText(
            value = pushToken,
            defaultValue = "No Push Token on Profile",
            label = "Push Token",
            onTextCopied = onCopyPushToken,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FormRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(2.dp)
        ) {
            Button(
                enabled = true,
                onClick = onRequestPushToken,
                elevation = ButtonDefaults.elevation(0.dp),
                shape = CircleShape,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Set SDK Token",
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            Button(
                enabled = true,
                onClick = onExpirePushToken,
                elevation = ButtonDefaults.elevation(0.dp),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = "Expire Push Token",
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            Button(
                enabled = isPushEnabled,
                onClick = onSendLocalNotification,
                elevation = ButtonDefaults.elevation(0.dp),
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = "Create Notification",
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FormRow {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(modifier = Modifier.padding(0.dp)) {
                    OutlinedTextField(
                        label = { Text("Server URL") },
                        value = viewState.baseUrl.value,
                        onValueChange = { input: String ->
                            viewState.baseUrl.value = input
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send, keyboardType = KeyboardType.Uri
                        ),
                        keyboardActions = KeyboardActions(onSend = { setBaseUrl() }),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier
                                    .clickable(
                                        onClick = {
                                            focusManager.clearFocus()
                                            setBaseUrl()
                                        },
                                        enabled = true,
                                        interactionSource = serverSetInteractionSource,
                                        indication = rememberRipple(bounded = true),
                                    )
                                    .padding(16.dp)
                            )
                        },
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .focusRequester(focusRequester),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = true,
                        onClick = {
                            viewState.baseUrl.value = "http://a.local-klaviyo.com:8080"
                            focusManager.clearFocus()
                            setBaseUrl()
                        },
                        elevation = ButtonDefaults.elevation(0.dp),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Local")
                    }
                    Button(
                        enabled = true,
                        onClick = {
                            viewState.baseUrl.value = "https://subdomain.ngrok.io"
                            focusRequester.requestFocus()
                        },
                        elevation = ButtonDefaults.elevation(0.dp),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Ngrok")
                    }
                    Button(
                        enabled = true,
                        onClick = {
                            viewState.baseUrl.value = "https://a.klaviyo.com"
                            focusManager.clearFocus()
                            setBaseUrl()
                        },
                        elevation = ButtonDefaults.elevation(0.dp),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Prod")
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = "App Version: ${AppBuildConfig.VERSION_NAME}",
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "SDK Version: ${SdkBuildConfig.VERSION}",
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview(group = "Settings", showSystemUi = true)
@Composable
private fun DisabledPushSettings() {
    Settings(
        SettingsViewModel.ViewState(
            isNotificationPermitted = false,
            pushToken = "alkj4h2to9s87rglknaucy490w37tnv0w3857cscwo87n5syos857nycoyoli3yhsj",
            baseUrl = mutableStateOf("https://a.klaviyo.com")
        )
    )
}

@SuppressLint("UnrememberedMutableState")
@Preview(group = "Settings")
@Composable
private fun EnabledPushSettings() {
    Settings(
        SettingsViewModel.ViewState(
            isNotificationPermitted = true,
            pushToken = "alkj4h2to9s87rglknaucy490w37tnv0w3857cscwo87n5syos857nycoyoli3yhsj",
            baseUrl = mutableStateOf("https://a.klaviyo.com")
        )
    )
}
