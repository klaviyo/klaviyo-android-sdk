package com.klaviyo.sdktestapp.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import java.util.logging.Level
import java.util.logging.Logger

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
        Logger.getLogger("SET_API_KEY").log(Level.INFO, viewModel.accountId.value)
        Registry.configBuilder.apiKey(viewModel.accountId.value)
    }

    fun create() {
        Logger.getLogger("CREATE_PROFILE").log(Level.INFO, "External ID: ${viewModel.externalId.value}, Email: ${viewModel.email.value}, Phone Number: ${viewModel.phoneNumber.value}")
        Klaviyo.setExternalId(viewModel.externalId.value).setEmail(viewModel.email.value).setPhoneNumber(viewModel.phoneNumber.value)
        refreshViewModel()
    }

    fun reset() {
        Logger.getLogger("CLEAR_PROFILE").log(Level.INFO, "Cleared all profile identifiers from state")
        Klaviyo.resetProfile()
        refreshViewModel()
    }

    fun copyAnonymousId() {
        Logger.getLogger("Anonymous ID").log(Level.INFO, viewModel.anonymousId)
        val clipData = ClipData.newPlainText("Anonymous ID", viewModel.anonymousId)
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(clipData)
    }
}
