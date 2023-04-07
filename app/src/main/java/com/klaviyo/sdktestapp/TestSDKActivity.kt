package com.klaviyo.sdktestapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Colors
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.primarySurface
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.sdktestapp.view.EventsPage
import com.klaviyo.sdktestapp.view.TopBar
import com.klaviyo.sdktestapp.viewmodel.AccountInfoViewModel
import com.klaviyo.sdktestapp.viewmodel.EventsViewModel
import com.klaviyo.sdktestapp.viewmodel.NavigationState
import com.klaviyo.sdktestapp.viewmodel.NavigationViewModel
import com.klaviyo.sdktestapp.viewmodel.PushSettingsViewModel
import com.klaviyo.sdktestapp.viewmodel.TabIndex
import com.klaviyo.sdktestapp.viewmodel.TabRowItem

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

@Composable
fun MainScreen(
    navigationViewModel: NavigationViewModel,
    accountInfoViewModel: AccountInfoViewModel,
    eventsViewModel: EventsViewModel,
    pushSettingsViewModel: PushSettingsViewModel,
) {
    val scaffoldState = rememberScaffoldState()

    MaterialTheme(
        colors = Colors(
            background = Color(0xFFE5E5E5), //
            primary = Color(0xFF373F47), //
            primaryVariant = Color(0xFF00FF00),
            secondary = Color(0xFF0000FF),
            secondaryVariant = Color(0xFFFF00FF),
            error = Color(0xFFFFFF00),
            surface = Color.White, //
            onSurface = Color(0xFF333333), //
            onBackground = Color(0xFF000000),
            onError = Color(0xFF888888),
            onPrimary = Color.White, //
            onSecondary = Color(0xFF88FF88),
            isLight = true,
        ),
    ) {
        Scaffold(
            scaffoldState = scaffoldState,
            topBar = { TopBar(navigationViewModel.navState) },
            floatingActionButton = { ActionButton(navigationViewModel.navState.floatingAction) },
            bottomBar = {
                BottomBar(
                    navigationViewModel.tabRowItems,
                    navigationViewModel.navState.tab.index
                ) { index ->
                    navigationViewModel.navigateTo(TabIndex.fromIndex(index))
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (navigationViewModel.navState.tab) {
                    TabIndex.Profile -> AccountInfo(
                        viewState = accountInfoViewModel.viewState,
                        setApiKey = accountInfoViewModel::setApiKey,
                        onCreate = accountInfoViewModel::create,
                        onClear = accountInfoViewModel::reset,
                        onCopyAnonymousId = accountInfoViewModel::copyAnonymousId,
                    )
                    TabIndex.Events -> EventsPage(
                        events = eventsViewModel.viewState.events,
                        selectedEvent = eventsViewModel.detailEvent,
                        onAddClicked = eventsViewModel::createEvent,
                        onClearClicked = eventsViewModel::clearEvents,
                        onEventClick = eventsViewModel::selectEvent,
                        onCopyClicked = eventsViewModel::copyEvent,
                        onNavigate = navigationViewModel::onNavigate
                    )
                    TabIndex.Settings -> PushSettings(
                        viewState = pushSettingsViewModel.viewState,
                        onCopyPushToken = pushSettingsViewModel::copyPushToken,
                        onOpenNotificationSettings = pushSettingsViewModel::openSettings,
                        onRequestedPushNotification = pushSettingsViewModel::requestPushNotifications,
                        onRequestPushToken = pushSettingsViewModel::setSdkPushToken,
                        onSendLocalNotification = pushSettingsViewModel::sendLocalNotification,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(action: NavigationState.Action?) {
    if (action == null) return

    FloatingActionButton(
        backgroundColor = MaterialTheme.colors.primarySurface,
        onClick = action.onClick
    ) {
        Icon(
            imageVector = action.imageVector(),
            contentDescription = action.contentDescription
        )
    }
}

@Composable
private fun BottomBar(tabRowItems: List<TabRowItem>, currentPage: Int, onTabClick: (Int) -> Unit) {
    BottomAppBar {
        tabRowItems.forEachIndexed { index, tabRow ->
            BottomNavigationItem(
                icon = {
                    Icon(
                        imageVector = tabRow.imageVector(),
                        contentDescription = tabRow.title,
                    )
                },
                selected = index == currentPage,
                onClick = { onTabClick(index) },
                label = { Text(text = tabRow.title) }
            )
        }
    }
}
