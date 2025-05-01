package com.klaviyo.forms.overlay

interface OverlayPresentationManager {
    fun preloadWebView()
    fun onPreloadComplete()
    fun presentOverlay()

    /**
     * TODO Close the form
     * Detach the webview from the activity
     * Dismiss the activity
     */
    fun dismissOverlay()
}