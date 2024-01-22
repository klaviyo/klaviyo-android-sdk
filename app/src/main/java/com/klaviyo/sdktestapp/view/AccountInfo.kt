package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.klaviyo.sdktestapp.viewmodel.AccountInfoViewModel

@OptIn(ExperimentalMaterial3Api::class) // Outlined text fields in Material 3 have nice caption text for error states but they are experimental so you have to opt in
@Composable
fun AccountInfo(
    viewState: AccountInfoViewModel.ViewState,
    setApiKey: () -> Unit = { },
    onCreate: () -> Unit = { },
    onClear: () -> Unit = {},
    onCopyAnonymousId: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        val focusManager = LocalFocusManager.current
        val keyboardActions = KeyboardActions(
            onNext = {
                // Select next input with "next" action
                focusManager.moveFocus(FocusDirection.Down)
            },
            onSend = {
                // Submit the form with "send" action
                onCreate()
                focusManager.clearFocus()
            }
        )

        FormRow {
            val interactionSource = remember { MutableInteractionSource() }

            OutlinedTextField(
                label = { Text("Account ID") },
                value = viewState.accountId.value,
                onValueChange = { input: String ->
                    viewState.accountId.value = input
                },
                isError = viewState.accountId.value.length != 6,
                supportingText = {
                    if (viewState.accountId.value.isEmpty()) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "Please enter an account ID",
                            color = Color.ErrorRed
                        )
                    } else if (viewState.accountId.value.length != 6) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "Account ID must be 6 characters",
                            color = Color.ErrorRed
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { setApiKey() }),
                modifier = Modifier.weight(1f, fill = true),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    setApiKey()
                                    focusManager.moveFocus(FocusDirection.Down)
                                },
                                enabled = true,
                                interactionSource = interactionSource,
                                indication = rememberRipple(bounded = true)
                            )
                            .padding(16.dp)
                    )
                }
            )
        }
        Divider()
        FormRow {
            OutlinedTextField(
                label = { Text("External ID") },
                value = viewState.externalId.value,
                onValueChange = { input: String ->
                    viewState.externalId.value = input
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Ascii
                ),
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f, fill = true)
            )
        }
        FormRow {
            OutlinedTextField(
                label = { Text("Email") },
                value = viewState.email.value,
                onValueChange = { input: String ->
                    viewState.email.value = input
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Email
                ),
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f, fill = true)
            )
        }
        FormRow {
            OutlinedTextField(
                label = { Text("Phone Number") },
                value = viewState.phoneNumber.value,
                onValueChange = { input: String ->
                    viewState.phoneNumber.value = input
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Phone
                ),
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f, fill = true)
            )
        }
        FormRow {
            CopyText(
                value = viewState.anonymousId.value,
                defaultValue = "No Anonymous ID",
                label = "Anonymous ID",
                onTextCopied = onCopyAnonymousId
            )
        }
        FormRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                elevation = ButtonDefaults.elevation(0.dp),
                shape = CircleShape,
                onClick = { onCreate() },
                enabled = viewState.accountId.value.length == 6,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Create Profile")
            }

            Button(
                elevation = ButtonDefaults.elevation(0.dp),
                shape = CircleShape,
                onClick = { onClear() },
                enabled = viewState.externalId.value.isNotEmpty() ||
                    viewState.email.value.isNotEmpty() ||
                    viewState.phoneNumber.value.isNotEmpty() ||
                    viewState.anonymousId.value.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Reset Profile")
            }
        }
    }
}

private fun makePreviewState(
    accountId: String = "XXXXXX",
    externalId: String = "123456,7890",
    email: String = "test@test.com",
    phoneNumber: String = "+155512345678",
    anonymousId: String = "f3c03998-4dbb-49cf-91f2-2a5ffb5d817c"
): AccountInfoViewModel.ViewState {
    return AccountInfoViewModel.ViewState(
        accountId = mutableStateOf(accountId),
        externalId = mutableStateOf(externalId),
        email = mutableStateOf(email),
        phoneNumber = mutableStateOf(phoneNumber),
        anonymousId = mutableStateOf(anonymousId)
    )
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun FilledAccountInfo() {
    val viewModelState = remember { makePreviewState() }
    AccountInfo(viewModelState)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoNoAccountId() {
    val viewModelState = remember { makePreviewState(accountId = "") }
    AccountInfo(viewModelState)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoDisabledClearButton() {
    val viewModelState = remember {
        makePreviewState(
            externalId = "",
            email = "",
            phoneNumber = ""
        )
    }
    AccountInfo(viewModelState)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoInvalidAccountId() {
    val viewModelState = remember { makePreviewState(accountId = "XXXXXXY") }
    AccountInfo(viewModelState)
}
