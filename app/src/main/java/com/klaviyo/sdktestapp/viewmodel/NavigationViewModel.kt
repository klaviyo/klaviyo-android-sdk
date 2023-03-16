package com.klaviyo.sdktestapp.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

class NavigationViewModel(firstState: NavigationState) {
    var navState by mutableStateOf(firstState)

    fun onNavigate(newNavigationState: NavigationState) {
        navState = newNavigationState
    }
}

data class NavigationState(
    val title: String,
    val navAction: Action? = null,
    val actions: List<Action>? = null,
) {
    companion object {
        fun makeBackButton(navAction: () -> Unit) = Action(
            imageVector = { Icons.Default.ArrowBack },
            contentDescription = "Back",
            onClick = navAction
        )
    }

    constructor(title: String, navAction: Action? = null, vararg actions: Action) : this(
        title,
        navAction,
        actions.toList()
    )

    data class Action(
        val imageVector: () -> ImageVector,
        val contentDescription: String,
        val onClick: () -> Unit
    )
}
