package com.klaviyo.sdktestapp.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import java.util.logging.Level
import java.util.logging.Logger

class Clipboard(private val context: Context) {

    fun copy(label: String, value: String) {
        val clipData = ClipData.newPlainText(label, value)
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(clipData)
    }

    fun logAndCopy(label: String, value: String, level: Level = Level.INFO) {
        copy(label = label, value = value)
        Logger.getLogger(label).log(level, value)
    }
}
