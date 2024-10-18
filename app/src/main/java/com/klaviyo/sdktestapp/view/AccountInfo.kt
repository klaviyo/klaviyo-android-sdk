@file:OptIn(ExperimentalFoundationApi::class)

package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.sdktestapp.viewmodel.AccountInfoViewModel
import com.klaviyo.sdktestapp.viewmodel.NavigationState
import com.klaviyo.sdktestapp.viewmodel.TabIndex

@Composable
fun AccountInfo(
    viewState: AccountInfoViewModel.ViewState,
    setProfile: () -> Unit = {},
    setApiKey: () -> Unit = {},
    setExternalId: () -> Unit = {},
    setEmail: () -> Unit = {},
    setPhoneNumber: () -> Unit = {},
    resetProfile: () -> Unit = {},
    copyAnonymousId: () -> Unit = {},
    setAttribute: (ProfileKey) -> Unit = {},
    onNavigate: (NavigationState) -> Unit = {}
) {
    var openAlertDialog by remember { mutableStateOf(false) }

    onNavigate(
        NavigationState(
            TabIndex.Profile,
            title = TabIndex.Profile.title,
            navAction = null,
            floatingAction = null,
            NavigationState.Action(
                imageVector = { Icons.Default.Info },
                contentDescription = "Info",
                onClick = {
                    openAlertDialog = true
                }
            )
        )
    )

    if (openAlertDialog) {
        AlertDialog(
            icon = {
                Icon(Icons.Default.Info, contentDescription = "Information")
            },
            text = {
                Text(
                    text = """
                        Values on this form are reflective of the internal state of the SDK.
                        Profile attribute values like name and address will disappear shortly 
                        after setting, because attributes are only saved in the SDK as long 
                        as it takes to enqueue an API request. 
                    """.trimIndent()
                )
            },
            onDismissRequest = { openAlertDialog = false },
            confirmButton = {
                TextButton(
                    onClick = { openAlertDialog = false }
                ) {
                    Text("Close")
                }
            }
        )
    }

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
                setProfile()
                focusManager.clearFocus()
            }
        )

        FormRow {
            OutlinedTextField(
                label = { Text("Company ID") },
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
                    EntryTrailingButton(text = "Start") {
                        setApiKey()
                        focusManager.moveFocus(FocusDirection.Down)
                    }
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
                modifier = Modifier.weight(1f, fill = true),
                trailingIcon = {
                    EntryTrailingButton { setExternalId() }
                }
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
                modifier = Modifier.weight(1f, fill = true),
                trailingIcon = {
                    EntryTrailingButton(onClick = setEmail)
                }
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
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Phone
                ),
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f, fill = true),
                trailingIcon = {
                    EntryTrailingButton(onClick = setPhoneNumber)
                }
            )
        }

        CollapsibleSection(
            "Profile Attributes",
            viewState,
            setAttribute = { setAttribute(it) },
            keyboardActions,
            arrayOf(
                ProfileKey.FIRST_NAME,
                ProfileKey.LAST_NAME,
                ProfileKey.ORGANIZATION,
                ProfileKey.TITLE,
                ProfileKey.IMAGE
            )
        )

        CollapsibleSection(
            "Location Attributes",
            viewState,
            setAttribute = { setAttribute(it) },
            keyboardActions,
            arrayOf(
                ProfileKey.ADDRESS1,
                ProfileKey.ADDRESS2,
                ProfileKey.CITY,
                ProfileKey.COUNTRY,
                ProfileKey.LATITUDE,
                ProfileKey.LONGITUDE,
                ProfileKey.REGION,
                ProfileKey.ZIP,
                ProfileKey.TIMEZONE
            )
        )

        FormRow {
            CopyText(
                value = viewState.anonymousId.value,
                defaultValue = "No Anonymous ID",
                label = "Anonymous ID",
                onTextCopied = copyAnonymousId
            )
        }
        FormRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                elevation = ButtonDefaults.elevation(0.dp),
                shape = CircleShape,
                onClick = { setProfile() },
                enabled = viewState.accountId.value.length == 6,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Set Profile")
            }

            Button(
                elevation = ButtonDefaults.elevation(0.dp),
                shape = CircleShape,
                onClick = { resetProfile() },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Reset Profile")
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    name: String,
    viewState: AccountInfoViewModel.ViewState,
    setAttribute: (ProfileKey) -> Unit,
    keyboardActions: KeyboardActions,
    attributes: Array<ProfileKey>
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true)
            ) {
                isOpen = !isOpen
            }
    ) {
        if (isOpen) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Collapse Section"
            )
        } else {
            Icon(imageVector = Icons.Default.ArrowRight, contentDescription = "Expand Section")
        }
        Text(name, modifier = Modifier.padding(8.dp))
    }

    attributes.takeIf { isOpen }?.forEach {
        FormRow {
            OutlinedTextField(
                label = { Text(it.displayName) },
                value = viewState.attributes.value[it]?.toString() ?: "",
                onValueChange = { input: String ->
                    viewState.attributes.value = viewState.attributes.value.copy().setProperty(
                        it,
                        input
                    )
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Ascii
                ),
                keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f, fill = true),
                trailingIcon = {
                    EntryTrailingButton { setAttribute(it) }
                }
            )
        }
    }
}

@Composable
private fun EntryTrailingButton(
    text: String = "Set",
    onClick: () -> Unit
) {
    Button(
        enabled = true,
        onClick = onClick,
        elevation = ButtonDefaults.elevation(0.dp),
        shape = CircleShape,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        Text(text = text)
    }
}

val accountPreviewState = AccountInfoViewModel.ViewState(
    accountId = mutableStateOf("XXXXXX"),
    externalId = mutableStateOf("1234567890"),
    email = mutableStateOf("test@test.com"),
    phoneNumber = mutableStateOf("+155512345678"),
    anonymousId = mutableStateOf("f3c03998-4dbb-49cf-91f2-2a5ffb5d817c"),
    attributes = mutableStateOf(
        Profile(
            mapOf(
                ProfileKey.FIRST_NAME to "Kermit",
                ProfileKey.LAST_NAME to "The Frog"
            )
        )
    )
)

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun FilledAccountInfo() {
    val viewModelState = remember { accountPreviewState }
    AccountInfo(viewModelState)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoNoAccountId() {
    val viewModelState = remember { accountPreviewState.copy(accountId = mutableStateOf("")) }
    AccountInfo(viewModelState)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoDisabledClearButton() {
    val viewModelState = remember {
        accountPreviewState.copy(
            accountId = mutableStateOf(""),
            email = mutableStateOf(""),
            phoneNumber = mutableStateOf("")
        )
    }
    AccountInfo(viewModelState)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoInvalidAccountId() {
    val viewModelState =
        remember { accountPreviewState.copy(accountId = mutableStateOf("XXXXXXY")) }
    AccountInfo(viewModelState)
}

private val ProfileKey.displayName: String
    get() {
        val pattern = "_[a-z]".toRegex()
        return name.replace(pattern) { " ${it.value.last().uppercase()}" }
            .replaceFirstChar { it.uppercase() }
    }
