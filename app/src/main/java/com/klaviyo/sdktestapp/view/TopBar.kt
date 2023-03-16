package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.PestControlRodent
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.klaviyo.sdktestapp.viewmodel.NavigationState

@Composable
fun TopBar(navState: NavigationState) {
    TopAppBar(
        title = {
            Text(
                text = navState.title,
                style = MaterialTheme.typography.h6,
                textAlign = navState.navAction?.let { TextAlign.Left } ?: TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = navState.navAction?.let { { NavButton(navAction = it) } },
        actions = navState.actions?.let { { it.map { NavButton(navAction = it) } } } ?: {},
        modifier = Modifier.fillMaxWidth(),
        elevation = 1.dp,
    )
}

@Composable
fun NavButton(navAction: NavigationState.Action) {
    val interactionSource = remember { MutableInteractionSource() }

    Icon(
        imageVector = navAction.imageVector(),
        contentDescription = navAction.contentDescription,
        modifier = Modifier
            .padding(8.dp)
            .clickable(
                onClick = navAction.onClick,
                enabled = true,
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
            )
    )
}

@Preview
@Composable
fun RootTopBar() {
    TopBar(NavigationState(title = "Preview"))
}

@Preview
@Composable
fun RootTopBarWithActions() {
    TopBar(
        NavigationState(
            title = "Preview",
            actions = listOf(
                NavigationState.Action(
                    imageVector = { Icons.Default.PestControl },
                    contentDescription = "Bug",
                ) {},
                NavigationState.Action(
                    imageVector = { Icons.Default.PestControlRodent },
                    contentDescription = "Mouse",
                ) {},
            )
        )
    )
}

@Preview
@Composable
fun DetailTopBar() {
    TopBar(NavigationState(title = "Detail Page", navAction = NavigationState.makeBackButton {}))
}

@Preview
@Composable
fun DetailTopBarWithActions() {
    TopBar(
        NavigationState(
            title = "Detail Page",
            navAction = NavigationState.makeBackButton {},
            actions = listOf(
                NavigationState.Action(
                    imageVector = { Icons.Default.PestControl },
                    contentDescription = "Bug",
                ) {},
                NavigationState.Action(
                    imageVector = { Icons.Default.PestControlRodent },
                    contentDescription = "Mouse",
                ) {},
            )
        )
    )
}
