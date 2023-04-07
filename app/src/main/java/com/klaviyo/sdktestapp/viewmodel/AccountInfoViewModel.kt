package com.klaviyo.sdktestapp.viewmodel

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.sdktestapp.TestApp
import com.klaviyo.sdktestapp.services.Clipboard
import com.klaviyo.sdktestapp.services.PushService

class AccountInfoViewModel(private val context: Context) {

    private companion object {
        const val ANON_KEY = "anonymous_id"
    }

    data class ViewState(
        var accountId: MutableState<String>,
        var externalId: MutableState<String>,
        var email: MutableState<String>,
        var phoneNumber: MutableState<String>,
        var anonymousId: String,
    )

    var viewState by mutableStateOf(
        ViewState(
            accountId = mutableStateOf(accountId),
            externalId = mutableStateOf(externalId),
            email = mutableStateOf(email),
            phoneNumber = mutableStateOf(phoneNumber),
            anonymousId = anonymousId
        )
    )
        private set

    private val accountId: String get() = Registry.config.apiKey

    private val externalId: String get() = Klaviyo.getExternalId() ?: ""

    private val email: String get() = Klaviyo.getEmail() ?: ""

    private val phoneNumber: String get() = Klaviyo.getPhoneNumber() ?: ""

    private val anonymousId: String get() = Registry.dataStore.fetch(ANON_KEY) ?: ""

    init {
        // Anonymous ID is generated internally, so we can just observe for it being set
        Registry.dataStore.onStoreChange { key, _ -> if (key == ANON_KEY) refreshViewModel() }
    }

    private fun refreshViewModel() {
        viewState = ViewState(
            accountId = mutableStateOf(accountId),
            externalId = mutableStateOf(externalId),
            email = mutableStateOf(email),
            phoneNumber = mutableStateOf(phoneNumber),
            anonymousId = anonymousId,
        )
    }

    fun setApiKey() {
        val app = (context as Activity).application
        val companyService = (app as TestApp).companyService
        companyService.setCompanyId(viewState.accountId.value)
    }

    fun create() {
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

        refreshViewModel()
    }

    fun reset() {
        Registry.log.info("Clear profile identifiers from state")
        Klaviyo.resetProfile()
        refreshViewModel()
    }

    fun copyAnonymousId() {
        Clipboard(context).logAndCopy("Anonymous ID", viewState.anonymousId)
    }
}
