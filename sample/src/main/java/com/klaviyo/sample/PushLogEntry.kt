package com.klaviyo.sample

/**
 * A single push notification captured for display in the sample app's Push Log screen — the
 * Android counterpart to the iOS example app's Push Log ("mobile inbox") POC.
 */
data class PushLogEntry(
    val id: String,
    val receivedAt: Long,
    val source: Source,
    val title: String,
    val body: String,
    val customData: Map<String, String>
) {
    /**
     * How the push reached the app. Unlike iOS — where the app state (foreground/background/tapped)
     * dictates which delegate fires — Klaviyo formats every push as an FCM data message, so all of
     * them arrive via [SamplePushService.onMessageReceived] regardless of app state. The meaningful
     * distinction on Android is therefore whether the push carried a visible alert.
     */
    enum class Source(val label: String) {
        /** Standard visible push: carries a title and/or body. */
        NOTIFICATION("Notification"),

        /** Silent / data-only push: no visible alert, custom data only. */
        SILENT("Silent")
    }
}
