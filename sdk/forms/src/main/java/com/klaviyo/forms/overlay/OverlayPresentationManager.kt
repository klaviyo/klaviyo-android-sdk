package com.klaviyo.forms.overlay

interface OverlayPresentationManager {
    fun preloadWebView()
    fun onPreloadComplete()
    fun presentOverlay()
    fun dismissOverlay()
}
