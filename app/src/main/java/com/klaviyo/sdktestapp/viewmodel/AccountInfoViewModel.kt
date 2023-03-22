package com.klaviyo.sdktestapp.viewmodel

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.sdktestapp.services.Clipboard

class AccountInfoViewModel(
    private val context: Context
) {
    data class ViewModel(
        var accountId: MutableState<String>,
        var externalId: MutableState<String>,
        var email: MutableState<String>,
        var phoneNumber: MutableState<String>,
        var anonymousId: String,
    )

    var viewModel by mutableStateOf(
        ViewModel(
            accountId = mutableStateOf(""),
            externalId = mutableStateOf(""),
            email = mutableStateOf(""),
            phoneNumber = mutableStateOf(""),
            anonymousId = ""
        )
    )
        private set

    private fun getAccountId(): String {
        return Registry.config.apiKey
    }

    private fun getExternalId(): String {
        return Klaviyo.getExternalId() ?: ""
    }

    private fun getEmail(): String {
        return Klaviyo.getEmail() ?: ""
    }

    private fun getPhoneNumber(): String {
        return Klaviyo.getPhoneNumber() ?: ""
    }

    private fun getAnonymousId(): String {
        return Registry.dataStore.fetch("anonymous_id") ?: ""
    }

    fun refreshViewModel() {
        viewModel = ViewModel(
            accountId = mutableStateOf(getAccountId()),
            externalId = mutableStateOf(getExternalId()),
            email = mutableStateOf(getEmail()),
            phoneNumber = mutableStateOf(getPhoneNumber()),
            anonymousId = getAnonymousId(),
        )
    }

    fun setApiKey() {
        Registry.log.info(viewModel.accountId.value)
        Registry.configBuilder.apiKey(viewModel.accountId.value)
    }

    fun create() {
        Registry.log.info("External ID: ${viewModel.externalId.value}, Email: ${viewModel.email.value}, Phone Number: ${viewModel.phoneNumber.value}")
        Klaviyo.setExternalId(viewModel.externalId.value).setEmail(viewModel.email.value).setPhoneNumber(viewModel.phoneNumber.value)
        refreshViewModel()
    }

    fun reset() {
        Registry.log.info("Clear profile identifiers from state")
        Klaviyo.resetProfile()
        refreshViewModel()
    }

    fun copyAnonymousId() {
        Clipboard(context).logAndCopy("Anonymous ID", viewModel.anonymousId)
    }
}
