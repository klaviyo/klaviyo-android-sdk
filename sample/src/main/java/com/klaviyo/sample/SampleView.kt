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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.klaviyo.sample.ui.theme.KlaviyoAndroidSdkTheme

private object UiConstants {
    // Toast Messages
    const val PROFILE_SET = "Profile set successfully"
    const val PROFILE_RESET = "Profile reset successfully"
    const val TEST_EVENT_CREATED = "Test event created"
    const val VIEWED_PRODUCT_CREATED = "Viewed product event created"
    const val FORMS_REGISTERED = "Registered for in-app forms"
    const val FORMS_UNREGISTERED = "Unregistered from in-app forms"

    // Section Headers
    const val PROFILE_SECTION = "Set Profile Information"
    const val EVENTS_SECTION = "Create Events"
    const val FORMS_SECTION = "In-App Forms"
    const val PUSH_SECTION = "Push Notifications"

    // Labels
    const val EXTERNAL_ID_LABEL = "External ID"
    const val EMAIL_LABEL = "Email"
    const val PHONE_LABEL = "Phone Number"
    const val PUSH_TOKEN_LABEL = "Push Token"

    // Button Text
    const val SET_PROFILE = "Set Profile"
    const val RESET_PROFILE = "Reset Profile"
    const val CREATE_TEST_EVENT = "Create Test Event"
    const val VIEWED_PRODUCT = "Viewed Product"
    const val REGISTER = "Register"
    const val UNREGISTER = "Unregister"
    const val REQUEST_PERMISSION = "Request Notification Permission"
    const val PERMISSION_GRANTED = "Notification Permission Granted"
}

@Composable
fun SampleView(
    viewModel: SampleViewModel,
    onRequestNotificationPermission: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    fun executeWithToast(
        action: () -> Unit,
        message: String
    ): () -> Unit = {
        action()
        onShowToast(message)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val notificationManager = NotificationManagerCompat.from(context)
                viewModel.updateNotificationPermission(notificationManager.areNotificationsEnabled())
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    KlaviyoAndroidSdkTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            color = MaterialTheme.colorScheme.background
        ) {
            SampleViewContent(
                externalId = viewModel.externalId,
                email = viewModel.email,
                phoneNumber = viewModel.phoneNumber,
                pushToken = viewModel.pushToken,
                hasNotificationPermission = viewModel.hasNotificationPermission,
                onExternalIdChange = viewModel::updateExternalId,
                onEmailChange = viewModel::updateEmail,
                onPhoneNumberChange = viewModel::updatePhoneNumber,
                setExternalId = executeWithToast(
                    viewModel::setExternalId,
                    "External ID set"
                ),
                setEmail = executeWithToast(
                    viewModel::setEmail,
                    "Email set"
                ),
                setPhoneNumber = executeWithToast(
                    viewModel::setPhoneNumber,
                    "Phone number set"
                ),
                setProfile = executeWithToast(
                    viewModel::setProfile,
                    UiConstants.PROFILE_SET
                ),
                resetProfile = executeWithToast(
                    viewModel::resetProfile,
                    UiConstants.PROFILE_RESET
                ),
                createTestEvent = executeWithToast(
                    viewModel::createTestEvent,
                    UiConstants.TEST_EVENT_CREATED
                ),
                createViewedProductEvent = executeWithToast(
                    viewModel::createViewedProductEvent,
                    UiConstants.VIEWED_PRODUCT_CREATED
                ),
                registerForInAppForms = executeWithToast(
                    viewModel::registerForInAppForms,
                    UiConstants.FORMS_REGISTERED
                ),
                unregisterFromInAppForms = executeWithToast(
                    viewModel::unregisterFromInAppForms,
                    UiConstants.FORMS_UNREGISTERED
                ),
                requestPermission = onRequestNotificationPermission
            )
        }
    }
}

@Composable
private fun SampleViewContent(
    externalId: String,
    email: String,
    phoneNumber: String,
    pushToken: String,
    hasNotificationPermission: Boolean,
    onExternalIdChange: (String) -> Unit = {},
    onEmailChange: (String) -> Unit = {},
    onPhoneNumberChange: (String) -> Unit = {},
    setExternalId: () -> Unit = {},
    setEmail: () -> Unit = {},
    setPhoneNumber: () -> Unit = {},
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

        SectionHeader(UiConstants.PROFILE_SECTION)
        ProfileTextField(
            label = UiConstants.EXTERNAL_ID_LABEL,
            value = externalId,
            onValueChange = onExternalIdChange,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
            keyboardActions = keyboardActions,
            trailingIcon = {
                EntryTrailingButton(onClick = setExternalId)
            }
        )
        ProfileTextField(
            label = UiConstants.EMAIL_LABEL,
            value = email,
            onValueChange = onEmailChange,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            keyboardActions = keyboardActions,
            trailingIcon = {
                EntryTrailingButton(onClick = setEmail)
            }
        )
        ProfileTextField(
            label = UiConstants.PHONE_LABEL,
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Send,
            keyboardActions = keyboardActions,
            trailingIcon = {
                EntryTrailingButton(onClick = setPhoneNumber)
            }
        )
        ViewRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = UiConstants.SET_PROFILE,
                onClick = setProfile,
                modifier = Modifier.weight(1f)
            )

            ActionButton(
                text = UiConstants.RESET_PROFILE,
                onClick = resetProfile,
                enabled = externalId.isNotEmpty() || email.isNotEmpty() || phoneNumber.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }

        ViewRow { Divider() }

        SectionHeader(UiConstants.EVENTS_SECTION)
        ViewRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = UiConstants.CREATE_TEST_EVENT,
                onClick = createTestEvent,
                modifier = Modifier.weight(1f),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Science,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = "Test Event"
                    )
                }
            )
            ActionButton(
                text = UiConstants.VIEWED_PRODUCT,
                onClick = createViewedProductEvent,
                modifier = Modifier.weight(1f),
                icon = {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = "Create Test Event"
                    )
                }
            )
        }

        ViewRow { Divider() }

        SectionHeader(UiConstants.FORMS_SECTION)
        ViewRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = UiConstants.REGISTER,
                onClick = registerForInAppForms,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                text = UiConstants.UNREGISTER,
                onClick = unregisterFromInAppForms,
                modifier = Modifier.weight(1f)
            )
        }

        ViewRow { Divider() }

        SectionHeader(UiConstants.PUSH_SECTION)
        ViewRow {
            if (hasNotificationPermission) {
                Text(text = UiConstants.PERMISSION_GRANTED)
            } else {
                ActionButton(
                    text = UiConstants.REQUEST_PERMISSION,
                    onClick = requestPermission,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ViewRow(horizontalArrangement = Arrangement.Start) {
            Text(text = UiConstants.PUSH_TOKEN_LABEL, style = MaterialTheme.typography.labelMedium)
        }
        ViewRow {
            Text(text = pushToken, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    ViewRow(horizontalArrangement = Arrangement.Start) {
        Text(text = text, style = MaterialTheme.typography.titleSmall)
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
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
            .then(modifier),
        content = content
    )
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    ViewRow {
        OutlinedTextField(
            label = { Text(label) },
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
            keyboardActions = keyboardActions,
            trailingIcon = trailingIcon,
            modifier = Modifier.weight(1f, fill = true)
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null
) {
    OutlinedButton(
        shape = CircleShape,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        icon?.invoke()
        Text(text = text)
    }
}

@Composable
private fun EntryTrailingButton(
    text: String = "Set",
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        Text(text = text)
    }
}

@Preview(showBackground = true)
@Composable
fun SamplePreviewEmpty() {
    KlaviyoAndroidSdkTheme {
        SampleViewContent(
            externalId = "",
            email = "",
            phoneNumber = "",
            pushToken = "",
            hasNotificationPermission = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SamplePreviewFilled() {
    KlaviyoAndroidSdkTheme {
        SampleViewContent(
            externalId = "ABC123",
            email = "profile@test.com",
            phoneNumber = "+1234567890",
            pushToken = "abcdefghijklmnopqrstuvwxyz1234567890",
            hasNotificationPermission = false,
        )
    }
}
