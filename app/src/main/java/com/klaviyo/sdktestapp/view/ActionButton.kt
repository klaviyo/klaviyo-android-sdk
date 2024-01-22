package com.klaviyo.sdktestapp.view

import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.primarySurface
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.klaviyo.sdktestapp.viewmodel.NavigationState

@Composable
fun ActionButton(action: NavigationState.Action) {
    if (action.subActions.isNullOrEmpty()) {
        FloatingActionButton(
            containerColor = MaterialTheme.colors.primarySurface,
            onClick = action.onClick
        ) {
            Icon(
                imageVector = action.imageVector(),
                contentDescription = action.contentDescription
            )
        }
    } else {
        MultiFloatingActionButton(
            fabIcon = action.imageVector(),
            containerColor = MaterialTheme.colors.primarySurface,
            items = action.subActions.map { subAction ->
                FabItem(
                    icon = subAction.imageVector(),
                    label = subAction.contentDescription,
                    onFabItemClicked = subAction.onClick
                )
            }
        )
    }
}

@Preview
@Composable
fun FanOutMenu() {
    ActionButton(
        action = NavigationState.Action(
            imageVector = { Icons.Outlined.Add },
            contentDescription = "Add Event",
            onClick = {},
            subActions = listOf(
                NavigationState.Action(
                    imageVector = { Icons.Filled.RemoveRedEye },
                    contentDescription = "Viewed Product",
                    onClick = {}
                ),
                NavigationState.Action(
                    imageVector = { Icons.Filled.Search },
                    contentDescription = "Searched Products",
                    onClick = {}
                )
            )
        )
    )
}
