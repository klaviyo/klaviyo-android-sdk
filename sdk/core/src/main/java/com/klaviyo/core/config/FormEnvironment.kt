package com.klaviyo.core.config

enum class FormEnvironment(val templateName: String) {
    IN_APP("in-app"),
    WEB("web");

    companion object {
        fun fromString(value: String): FormEnvironment =
            when (value) {
                WEB.templateName -> WEB
                else -> IN_APP
            }
    }
}
