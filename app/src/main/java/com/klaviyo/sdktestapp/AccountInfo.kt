package com.klaviyo.sdktestapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.klaviyo.sdktestapp.view.CopyText
import com.klaviyo.sdktestapp.view.ErrorRed
import com.klaviyo.sdktestapp.viewmodel.AccountInfoViewModel

@OptIn(ExperimentalMaterial3Api::class) // Outlined text fields in Material 3 have nice caption text for error states but they are experimental so you have to opt in
@Composable
fun AccountInfo(
    viewModel: AccountInfoViewModel,
    setApiKey: () -> Unit = { },
    onCreate: () -> Unit = { },
    onClear: () -> Unit = {},
    onCopyAnonymousId: () -> Unit = {},
) {

    // Additional state to track whats written into the text fields by the user separately from whats stored in the sdk
    var changedAccountId by remember { mutableStateOf(viewModel.viewModel.accountId.value) }
    var changedExternalId by remember { mutableStateOf(viewModel.viewModel.externalId.value) }
    var changedEmail by remember { mutableStateOf(viewModel.viewModel.email.value) }
    var changedPhoneNumber by remember { mutableStateOf(viewModel.viewModel.phoneNumber.value) }

    // Any mutable state held for the text fields should be cleared by this function if related to a customer profile
    fun clearProfileMutables() {
        changedExternalId = ""
        changedEmail = ""
        changedPhoneNumber = ""
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = viewModel.viewModel.accountId.value,
                    onValueChange = { input: String ->
                        changedAccountId = input
                        viewModel.viewModel.accountId.value = input
                    },
                    label = { Text("Account ID") },
                    isError = viewModel.viewModel.accountId.value.length != 6,
                    supportingText = {
                        if (viewModel.viewModel.accountId.value.isEmpty()) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = "Please enter an account ID",
                                color = Color.ErrorRed
                            )
                        } else if (viewModel.viewModel.accountId.value.length != 6) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = "Account ID must be 6 characters",
                                color = Color.ErrorRed
                            )
                        }
                    },
                    modifier = Modifier.weight(1f, fill = true)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = viewModel.viewModel.externalId.value,
                    onValueChange = { input: String ->
                        changedExternalId = input
                        viewModel.viewModel.externalId.value = input
                    },
                    label = { Text("External ID") },
                    modifier = Modifier.weight(1f, fill = true)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = viewModel.viewModel.email.value,
                    onValueChange = { input: String ->
                        changedEmail = input
                        viewModel.viewModel.email.value = input
                    },
                    label = { Text("Email") },
                    modifier = Modifier.weight(1f, fill = true)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = viewModel.viewModel.phoneNumber.value,
                    onValueChange = { input: String ->
                        changedPhoneNumber = input
                        viewModel.viewModel.phoneNumber.value = input
                    },
                    label = { Text("Phone Number") },
                    modifier = Modifier.weight(1f, fill = true)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Box(modifier = Modifier.weight(1f, fill = true))
                CopyText(
                    value = viewModel.viewModel.anonymousId,
                    defaultValue = "No Anonymous ID",
                    label = "Anonymous ID",
                    onTextCopied = onCopyAnonymousId,
                )
                Box(modifier = Modifier.weight(1f, fill = true))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Box(modifier = Modifier.weight(1f, fill = true))
                Button(
                    elevation = ButtonDefaults.elevation(0.dp),
                    shape = CircleShape,
                    onClick = {
                        setApiKey()
                        onCreate()
                    },
                    enabled = viewModel.viewModel.accountId.value.length == 6
                ) {
                    Text(
                        text = "Create Profile",
                    )
                }
                Box(modifier = Modifier.weight(1f, fill = true))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Box(modifier = Modifier.weight(1f, fill = true))
                Button(
                    elevation = ButtonDefaults.elevation(0.dp),
                    shape = CircleShape,
                    onClick = {
                        clearProfileMutables()
                        onClear()
                    },
                    enabled = viewModel.viewModel.externalId.value.isNotEmpty() || viewModel.viewModel.email.value.isNotEmpty() || viewModel.viewModel.phoneNumber.value.isNotEmpty()
                ) {
                    Text(
                        text = "Clear",
                    )
                }
                Box(modifier = Modifier.weight(1f, fill = true))
            }
        }
    }
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun FilledAccountInfo() {
    val context = LocalContext.current
    val viewModel = AccountInfoViewModel(context)
    viewModel.viewModel.accountId.value = "XXXXXX"
    viewModel.viewModel.externalId.value = "1234567890"
    viewModel.viewModel.email.value = "test@test.com"
    viewModel.viewModel.phoneNumber.value = "+155512345678"
    viewModel.viewModel.anonymousId = "f3c03998-4dbb-49cf-91f2-2a5ffb5d817c"
    AccountInfo(viewModel)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoNoAccountId() {
    val context = LocalContext.current
    val viewModel = AccountInfoViewModel(context)
    viewModel.viewModel.accountId.value = ""
    viewModel.viewModel.externalId.value = "1234567890"
    viewModel.viewModel.email.value = "test@test.com"
    viewModel.viewModel.phoneNumber.value = "+155512345678"
    viewModel.viewModel.anonymousId = "f3c03998-4dbb-49cf-91f2-2a5ffb5d817c"
    AccountInfo(viewModel)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoDisabledClearButton() {
    val context = LocalContext.current
    val viewModel = AccountInfoViewModel(context)
    viewModel.viewModel.accountId.value = "XXXXXX"
    viewModel.viewModel.externalId.value = ""
    viewModel.viewModel.email.value = ""
    viewModel.viewModel.phoneNumber.value = ""
    viewModel.viewModel.anonymousId = "f3c03998-4dbb-49cf-91f2-2a5ffb5d817c"
    AccountInfo(viewModel)
}

@Preview(group = "AccountInfo", showSystemUi = true)
@Composable
private fun AccountInfoInvalidAccountId() {
    val context = LocalContext.current
    val viewModel = AccountInfoViewModel(context)
    viewModel.viewModel.accountId.value = "XXXXXX7"
    viewModel.viewModel.externalId.value = "1234567890"
    viewModel.viewModel.email.value = "test@test.com"
    viewModel.viewModel.phoneNumber.value = "+155512345678"
    viewModel.viewModel.anonymousId = "f3c03998-4dbb-49cf-91f2-2a5ffb5d817c"
    AccountInfo(viewModel)
}
