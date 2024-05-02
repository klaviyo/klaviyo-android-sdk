package com.klaviyo.sdktestapp.viewmodel

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.core.Registry
import com.klaviyo.sdktestapp.services.Clipboard
import com.klaviyo.sdktestapp.services.ConfigService
import com.klaviyo.sdktestapp.services.PushService

interface IAccountInfoViewModel {
    val viewState: AccountInfoViewModel.ViewState
    fun setApiKey()
    fun create()
    fun reset()
    fun copyAnonymousId()
}

class AccountInfoViewModel(private val context: Context) : IAccountInfoViewModel {

    private companion object {
        const val ANON_KEY = "anonymous_id"
    }

    data class ViewState(
        var accountId: MutableState<String>,
        var externalId: MutableState<String>,
        var email: MutableState<String>,
        var phoneNumber: MutableState<String>,
        var anonymousId: MutableState<String>
    )

    override val viewState = ViewState(
        accountId = mutableStateOf(Registry.config.apiKey),
        externalId = mutableStateOf(Klaviyo.getExternalId() ?: ""),
        email = mutableStateOf(Klaviyo.getEmail() ?: ""),
        phoneNumber = mutableStateOf(Klaviyo.getPhoneNumber() ?: ""),
        anonymousId = mutableStateOf(Registry.dataStore.fetch(ANON_KEY) ?: "")
    )

    init {
        // Observe persistent store for all identifier changes
        Registry.dataStore.onStoreChange { key, value ->
            when (key) {
                ANON_KEY -> viewState.anonymousId.value = value ?: ""
                ProfileKey.EXTERNAL_ID.name -> viewState.externalId.value = value ?: ""
                ProfileKey.EMAIL.name -> viewState.email.value = value ?: ""
                ProfileKey.PHONE_NUMBER.name -> viewState.phoneNumber.value = value ?: ""
            }
        }
    }

    override fun setApiKey() {
        Registry.get<ConfigService>().companyId = viewState.accountId.value
    }

    override fun create() {
        setApiKey() // For safety, make sure that the input API key has been set to SDK

        if (viewState.phoneNumber.value.firstOrNull()?.isDigit() == true) {
            // Try to help with the formatting, prepend a + to the number if they didn't yet
            viewState.phoneNumber.value = "+${viewState.phoneNumber.value}"
        }

        Registry.log.info("External ID: ${viewState.externalId.value}")
        Registry.log.info("Email: ${viewState.email.value}")
        Registry.log.info("Phone Number: ${viewState.phoneNumber.value}")

        // Set identifiers from form into SDK's current profile
        Klaviyo.setExternalId(viewState.externalId.value)
            .setEmail(viewState.email.value)
            .setPhoneNumber(viewState.phoneNumber.value)

        // Send push token along with the new profile
        PushService.setSdkPushToken()
    }

    override fun reset() {
        Registry.log.info("Clear profile identifiers from state")
        Klaviyo.resetProfile()
    }

    override fun copyAnonymousId() {
        Clipboard(context).logAndCopy("Anonymous ID", viewState.anonymousId.value)
    }
}
