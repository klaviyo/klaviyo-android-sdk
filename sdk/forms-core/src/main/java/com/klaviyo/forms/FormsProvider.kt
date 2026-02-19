package com.klaviyo.forms

interface FormsProvider {
    fun register(config: InAppFormsConfig)
    fun unregister()
    fun reInitialize()
}
