package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Colors
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.klaviyo.sdktestapp.viewmodel.AccountInfoViewModel
import com.klaviyo.sdktestapp.viewmodel.EventsViewModel
import com.klaviyo.sdktestapp.viewmodel.NavigationViewModel
import com.klaviyo.sdktestapp.viewmodel.SettingsViewModel
import com.klaviyo.sdktestapp.viewmodel.TabIndex
import com.klaviyo.sdktestapp.viewmodel.TabRowItem

@Composable
fun MainScreen(
    navigationViewModel: NavigationViewModel,
    accountInfoViewModel: AccountInfoViewModel,
    eventsViewModel: EventsViewModel,
    settingsViewModel: SettingsViewModel,
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
            floatingActionButton = {
                navigationViewModel.navState.floatingAction?.let { ActionButton(it) }
            },
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
                        onCreateEvent = eventsViewModel::createEvent,
                        onClearClicked = eventsViewModel::clearEvents,
                        onEventClick = eventsViewModel::selectEvent,
                        onCopyClicked = eventsViewModel::copyEvent,
                        onNavigate = navigationViewModel::onNavigate
                    )

                    TabIndex.Settings -> Settings(
                        viewState = settingsViewModel.viewState,
                        onCopyPushToken = settingsViewModel::copyPushToken,
                        onOpenNotificationSettings = settingsViewModel::openSettings,
                        onRequestedPushNotification = settingsViewModel::requestPushNotifications,
                        onRequestPushToken = settingsViewModel::setSdkPushToken,
                        onSendLocalNotification = settingsViewModel::sendLocalNotification,
                        setBaseUrl = settingsViewModel::setBaseUrl,
                    )
                }
            }
        }
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
