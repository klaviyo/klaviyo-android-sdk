package com.klaviyo.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()

private val DarkColors = darkColorScheme()

@Composable
fun KlaviyoAndroidSdkTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {
    MaterialTheme(
        colorScheme = if (!useDarkTheme) {
            LightColors
        } else {
            DarkColors
        },
        content = content
    )
}
