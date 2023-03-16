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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.primarySurface
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.sdktestapp.view.EventsPage
import com.klaviyo.sdktestapp.view.TopBar
import com.klaviyo.sdktestapp.viewmodel.AccountInfoViewModel
import com.klaviyo.sdktestapp.viewmodel.EventsViewModel
import com.klaviyo.sdktestapp.viewmodel.NavigationState
import com.klaviyo.sdktestapp.viewmodel.NavigationViewModel
import com.klaviyo.sdktestapp.viewmodel.PushSettingsViewModel
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.launch

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

    private val navigationViewModel = NavigationViewModel(NavigationState(""))
    private val accountInfoViewModel: AccountInfoViewModel = AccountInfoViewModel(this)
    private val eventsViewModel = EventsViewModel(this)
    private val pushSettingsViewModel: PushSettingsViewModel = PushSettingsViewModel(this, pushNotificationContract)

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

    override fun onResume() {
        super.onResume()
        Logger.getLogger("RESUME").log(Level.INFO, "resumed")
        pushSettingsViewModel.refreshViewModel()
        accountInfoViewModel.refreshViewModel()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Klaviyo.handlePush(intent)
    }
}

// TODO extract models and composables into individual files?
data class TabRowItem(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit,
)

@Composable
private fun TabScreen(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp),
    ) {
        content()
    }
}

@Composable
@OptIn(ExperimentalPagerApi::class)
fun MainScreen(
    navigationViewModel: NavigationViewModel,
    accountInfoViewModel: AccountInfoViewModel,
    eventsViewModel: EventsViewModel,
    pushSettingsViewModel: PushSettingsViewModel,
) {
    val tabRowItems = listOf(
        TabRowItem(
            title = "Account Info",
            screen = {
                TabScreen {
                    AccountInfo(
                        viewModel = accountInfoViewModel,
                        setApiKey = accountInfoViewModel::setApiKey,
                        onCreate = accountInfoViewModel::create,
                        onClear = accountInfoViewModel::reset,
                        onCopyAnonymousId = accountInfoViewModel::copyAnonymousId,
                    )
                }
            },
            icon = Icons.Outlined.AccountCircle,
        ),
        TabRowItem(
            title = "Events",
            screen = {
                TabScreen {
                    EventsPage(
                        events = eventsViewModel.viewState.events,
                        selectedEvent = eventsViewModel.detailEvent,
                        onClearClicked = eventsViewModel::clearEvents,
                        onEventClick = eventsViewModel::selectEvent,
                        onCopyClicked = eventsViewModel::copyEvent,
                        onNavigate = navigationViewModel::onNavigate
                    )
                }
            },
            icon = Icons.Outlined.Notifications,
        ),
        TabRowItem(
            title = "Push Settings",
            screen = {
                TabScreen {
                    PushSettings(
                        isPushEnabled = pushSettingsViewModel.viewModel.isPushEnabled,
                        pushToken = pushSettingsViewModel.viewModel.pushToken,
                        onCopyPushToken = pushSettingsViewModel::copyPushToken,
                        onOpenNotificationSettings = pushSettingsViewModel::openSettings,
                        onRequestedPushNotification = pushSettingsViewModel::requestPushNotifications
                    )
                }
            },
            icon = Icons.Outlined.Settings,
        )
    )
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()
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
            floatingActionButton = {
                // TODO Roll this into NavigationState?
                if (pagerState.currentPage == 1) {
                    FloatingActionButton(
                        backgroundColor = MaterialTheme.colors.primarySurface,
                        onClick = { eventsViewModel.createEvent() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create an Event",
                        )
                    }
                }
            },
            isFloatingActionButtonDocked = false,
            scaffoldState = scaffoldState,
            topBar = { TopBar(navigationViewModel.navState) },
            bottomBar = {
                BottomAppBar {
                    tabRowItems.forEachIndexed { index, tabRow ->
                        BottomNavigationItem(
                            icon = {
                                Icon(
                                    imageVector = tabRow.icon,
                                    contentDescription = tabRow.title,
                                )
                            },
                            selected = index == pagerState.currentPage,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            label = { Text(text = tabRow.title) }
                        )
                    }
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                count = tabRowItems.size,
                contentPadding = padding,
            ) {
                val tab = tabRowItems[pagerState.currentPage]
                navigationViewModel.onNavigate(NavigationState(tab.title))
                tab.screen()
            }
        }
    }
}
