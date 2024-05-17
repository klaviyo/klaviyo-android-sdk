package com.klaviyo.sdktestapp.viewmodel

import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

class NavigationViewModel {

    val tabRowItems = listOf(
        TabRowItem(tab = TabIndex.Profile),
        TabRowItem(tab = TabIndex.Events),
        TabRowItem(tab = TabIndex.Settings)
    )

    var navState: NavigationState by mutableStateOf(tabRowItems.first().getNavState())
        private set

    fun onNavigate(newNavigationState: NavigationState) {
        navState = newNavigationState
    }

    fun navigateTo(tab: TabIndex) {
        Firebase.analytics.logEvent("navigate", Bundle().apply { putString("tab", tab.name) })
        onNavigate(tabRowItems[tab.index].getNavState())
    }
}

enum class TabIndex(val index: Int) {
    Profile(0),
    Events(1),
    Settings(2);

    val title: String
        get() = when (this) {
            Profile -> "Account Info"
            Events -> "Events"
            Settings -> "Settings"
        }

    val icon: ImageVector
        get() = when (this) {
            Profile -> Icons.Outlined.AccountCircle
            Events -> Icons.Outlined.Notifications
            Settings -> Icons.Outlined.Settings
        }

    companion object {
        fun fromIndex(index: Int): TabIndex {
            return when (index) {
                0 -> Profile
                1 -> Events
                else -> Settings
            }
        }
    }
}

data class TabRowItem(val tab: TabIndex) {
    val title: String = tab.title
    val imageVector: ImageVector
        get() = tab.icon

    fun getNavState(): NavigationState = NavigationState(
        tab = tab,
        title = title
    )
}

/**
 * Represents overall navigation state, consumed by scaffold
 *
 * @property tab - Which tab index is displayed
 * @property title - Title of the page (default to tab's title)
 * @property navAction - Nav action button, in top left of scaffold
 * @property floatingAction - Floating action button definition
 * @property actions - Additional actions, top right
 */
data class NavigationState(
    val tab: TabIndex,
    val title: String,
    val navAction: Action? = null,
    val floatingAction: Action? = null,
    val actions: List<Action>? = null
) {
    companion object {
        fun makeBackButton(navAction: () -> Unit) = Action(
            imageVector = { Icons.Default.ArrowBack },
            contentDescription = "Back",
            onClick = navAction
        )
    }

    constructor(
        tab: TabIndex,
        title: String,
        navAction: Action? = null,
        floatingAction: Action? = null,
        vararg actions: Action
    ) : this(
        tab = tab,
        title = title,
        navAction = navAction,
        floatingAction = floatingAction,
        actions.toList()
    )

    data class Action(
        val imageVector: () -> ImageVector,
        val contentDescription: String,
        val subActions: List<Action>? = null,
        val onClick: () -> Unit
    )
}
