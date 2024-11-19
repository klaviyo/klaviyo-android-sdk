package com.klaviyo.analytics.networking.requests

data class FullsFormsResponse(
    val fullForms: List<String>,
    val formSettings: String,
    val dynamicInfoConfig: String
) {
    internal companion object {
        const val FULL_FORMS = "full_forms"
        const val FORM_SETTINGS = "form_settings"
        const val DYNAMIC_INFO_CONFIG = "dynamic_info_config"
    }
}
