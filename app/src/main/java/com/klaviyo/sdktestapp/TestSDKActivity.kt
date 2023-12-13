package com.klaviyo.sdktestapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.sdktestapp.view.MainScreen
import com.klaviyo.sdktestapp.viewmodel.AccountInfoViewModel
import com.klaviyo.sdktestapp.viewmodel.EventsViewModel
import com.klaviyo.sdktestapp.viewmodel.NavigationViewModel
import com.klaviyo.sdktestapp.viewmodel.PushSettingsViewModel
import com.klaviyo.sdktestapp.viewmodel.TabIndex

class TestSDKActivity : ComponentActivity() {

    private val pushNotificationContract =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // This is the callback from notification permission prompt. It is only called on API 33+
            pushSettingsViewModel.refreshViewModel()

            if (!granted) {
                // DENIED - Tell the user the consequences of their actions...
                pushSettingsViewModel.alertPermissionDenied()
            }
        }

    private val navigationViewModel = NavigationViewModel()

    private val accountInfoViewModel: AccountInfoViewModel = AccountInfoViewModel(this)

    private val eventsViewModel = EventsViewModel(this)

    private val pushSettingsViewModel: PushSettingsViewModel =
        PushSettingsViewModel(this, pushNotificationContract)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onNewIntent(intent)

        setContent {
            MainScreen(
                navigationViewModel = navigationViewModel,
                accountInfoViewModel = accountInfoViewModel,
                eventsViewModel = eventsViewModel,
                pushSettingsViewModel = pushSettingsViewModel,
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.data?.let {
            val path = it.host
            var index = 0
            when (path) {
                "accountInfo" -> index = 0
                "requestLog" -> index = 1
                "settings" -> index = 2
            }

            navigationViewModel.navigateTo(TabIndex.fromIndex(index))
        }

        Klaviyo.handlePush(intent)
    }
}
