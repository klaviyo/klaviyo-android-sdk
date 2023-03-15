package com.klaviyo.sdktestapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Colors
import androidx.compose.material.ContentAlpha
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.primarySurface
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.sdktestapp.view.EventsList
import com.klaviyo.sdktestapp.viewmodel.EventsViewModel
import com.klaviyo.sdktestapp.viewmodel.PushSettingsViewModel
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

    private val pushSettingsViewModel: PushSettingsViewModel = PushSettingsViewModel(this, pushNotificationContract)
    private val eventsViewModel = EventsViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onNewIntent(intent)

        setContent {
            MainScreen(
                pushSettingsViewModel = pushSettingsViewModel,
                eventsViewModel = eventsViewModel
            )
        }
    }

    override fun onResume() {
        super.onResume()
        pushSettingsViewModel.refreshViewModel()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Klaviyo.handlePush(intent)
    }
}

data class TabRowItem(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit,
)

@Composable
fun TabScreen(
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
    pushSettingsViewModel: PushSettingsViewModel,
    eventsViewModel: EventsViewModel
) {
    val tabRowItems = listOf(
        TabRowItem(
            title = "Account Info",
            screen = {
                TabScreen {
                    Text(
                        text = "Account Info Screen",
                        style = MaterialTheme.typography.body1,
                    )
                }
            },
            icon = Icons.Outlined.AccountCircle,
        ),
        TabRowItem(
            title = "Events",
            screen = {
                TabScreen {
                    // TODO action button to clear screen
                    EventsList(eventsViewModel.viewState.events) { event ->
                        // TODO navigate to event detail page
                        println(event)
                    }
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
            topBar = {
                TopAppBar(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProvideTextStyle(value = MaterialTheme.typography.h6) {
                            CompositionLocalProvider(
                                LocalContentAlpha provides ContentAlpha.high,
                            ) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    text = tabRowItems[pagerState.currentPage].title,
                                )
                            }
                        }
                    }
                }
            },
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
                tabRowItems[pagerState.currentPage].screen()
            }
        }
    }
}
