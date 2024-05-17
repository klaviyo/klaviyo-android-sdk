package com.klaviyo.sdktestapp.viewmodel

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.ImmutableProfile
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.sdktestapp.services.Clipboard
import com.klaviyo.sdktestapp.services.ConfigService

interface IAccountInfoViewModel {
    val viewState: AccountInfoViewModel.ViewState
    fun setApiKey(): IAccountInfoViewModel
    fun setProfile(): IAccountInfoViewModel
    fun setExternalId(): IAccountInfoViewModel
    fun setEmail(): IAccountInfoViewModel
    fun setPhoneNumber(): IAccountInfoViewModel
    fun setAttribute(key: ProfileKey): IAccountInfoViewModel
    fun resetProfile(): IAccountInfoViewModel
    fun copyAnonymousId()
}

class AccountInfoViewModel(private val context: Context) : IAccountInfoViewModel {

    private companion object {
        const val ANONYMOUS_ID = "anonymous_id"
    }

    data class ViewState(
        var accountId: MutableState<String>,
        var externalId: MutableState<String>,
        var email: MutableState<String>,
        var phoneNumber: MutableState<String>,
        var anonymousId: MutableState<String>,
        var attributes: MutableState<ImmutableProfile>
    ) {
        companion object {
            fun fromSdk() = ViewState(
                accountId = mutableStateOf(Registry.config.apiKey),
                externalId = mutableStateOf(Klaviyo.getExternalId() ?: ""),
                email = mutableStateOf(Klaviyo.getEmail() ?: ""),
                phoneNumber = mutableStateOf(Klaviyo.getPhoneNumber() ?: ""),
                anonymousId = mutableStateOf(Registry.dataStore.fetch(ANONYMOUS_ID) ?: ""),
                attributes = mutableStateOf(
                    Registry.get<State>().getAsProfile(withAttributes = true)
                )
            )
        }

        fun updateFromSdk() = fromSdk().also {
            externalId.value = it.externalId.value
            email.value = it.email.value
            phoneNumber.value = it.phoneNumber.value
            anonymousId.value = it.anonymousId.value
            attributes.value = it.attributes.value
        }
    }

    override val viewState = ViewState.fromSdk()

    init {
        Registry.get<State>().onStateChange { _, _ -> viewState.updateFromSdk() }
    }

    /**
     * Save API key to test app config service, which in turn re-initializes the SDK
     */
    override fun setApiKey() = apply {
        Registry.get<ConfigService>().companyId = viewState.accountId.value
    }

    /**
     * Verify the company ID is set, and call [Klaviyo.setProfile]
     */
    override fun setProfile() = setApiKey().formatPhone().apply {
        // Set identifiers from form into SDK's current profile
        Klaviyo.setProfile(
            Profile(
                externalId = viewState.externalId.value,
                email = viewState.email.value,
                phoneNumber = viewState.phoneNumber.value
            ).merge(viewState.attributes.value)
        )
    }

    override fun setExternalId() = setApiKey().apply {
        Klaviyo.setExternalId(viewState.externalId.value)
    }

    override fun setEmail() = setApiKey().apply {
        Klaviyo.setEmail(viewState.email.value)
    }

    private fun formatPhone() = apply {
        if (viewState.phoneNumber.value.firstOrNull()?.isDigit() == true) {
            // Try to help with the formatting, prepend a + to the number if they didn't yet
            viewState.phoneNumber.value = "+${viewState.phoneNumber.value}"
        }
    }

    override fun setPhoneNumber() = setApiKey().formatPhone().apply {
        Klaviyo.setPhoneNumber(viewState.phoneNumber.value)
    }

    override fun setAttribute(key: ProfileKey): IAccountInfoViewModel = setApiKey().apply {
        Klaviyo.setProfileAttribute(key, viewState.attributes.value[key]?.toString() ?: "")
    }

    override fun resetProfile() = setApiKey().apply {
        Klaviyo.resetProfile()
    }

    override fun copyAnonymousId() {
        Clipboard(context).logAndCopy("Anonymous ID", viewState.anonymousId.value)
    }
}
