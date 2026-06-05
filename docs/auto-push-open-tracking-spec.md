# Automatic Push Open Tracking — Android SDK Spec

## Overview

Today, integrators must call `Klaviyo.handlePush(intent)` manually in every Activity that can
receive a notification tap. This is boilerplate that's easy to miss, easy to duplicate, and
gets more complex when deep links and action buttons are involved.

This feature adds an opt-in mode where the SDK owns the notification tap entirely: it tracks
`Opened Push`, handles deep link routing, and forwards the user to the right destination —
without any host-side code required.

---

## Opt-in mechanism

The feature is disabled by default. Integrators opt in with a single line in their app's
`AndroidManifest.xml` under the `<application>` tag:

```xml
<meta-data
    android:name="com.klaviyo.push.automatic_open_tracking"
    android:value="true" />
```

The SDK reads this flag at notification build time via `Registry.config.getManifestBoolean(...)`.
No code changes are required in the host app once this flag is set.

---

## How it works

### Background: PendingIntents and notification taps

When the SDK displays a notification, it attaches a `PendingIntent` to it. A `PendingIntent` is
essentially a pre-authorized instruction: "when the user taps this notification, start this
Activity with this Intent." Normally, that Intent targets the host app's main Activity directly.

With auto-tracking enabled, that Intent targets `KlaviyoTrampolineActivity` instead.

### The Trampoline Activity

`KlaviyoTrampolineActivity` is a transparent, invisible Activity declared in the SDK's
`push-fcm` module manifest. Its sole job is to intercept the notification tap, perform
tracking, and immediately forward the user to the real destination.

**Manifest flags:**

| Flag | Value | Purpose |
|------|-------|---------|
| `exported` | `false` | Only reachable via `PendingIntent` from our own app |
| `launchMode` | `singleTask` | Handles rapid taps from multiple notifications cleanly |
| `taskAffinity` | `""` | Keeps the trampoline out of the host app's back stack |
| `noHistory` | `true` | Never appears in the back stack |
| `excludeFromRecents` | `true` | Never appears in the recents screen |
| `theme` | `Translucent.NoTitleBar` | Invisible while routing |

**Lifecycle:**

```
Notification tap
    └─► KlaviyoTrampolineActivity.onCreate()
            └─► handleTrampolineIntent(intent, context)
                    ├─► Klaviyo.handlePush(intent)        // tracks Opened Push
                    ├─► intent.putExtra(AUTO_TRACKED_EXTRA, true)   // dedup stamp
                    └─► startDestination(context, intent) // launch real destination
            └─► finish()                                  // trampoline disappears
```

`onNewIntent` mirrors `onCreate` to handle the case where `launchMode="singleTask"` re-enters
an existing trampoline instance on a rapid second tap.

### Intent routing in `startDestination`

The trampoline inspects `intent.data` to decide where to send the user:

```
intent.data != null (deep link present)
    └─► DeepLinking.makeDeepLinkIntent(uri, context, copyIntent = intent)
            ├─► resolveActivity != null  →  launch deep link Activity
            └─► resolveActivity == null  →  fall back to launcher Activity

intent.data == null
    └─► DeepLinking.makeLaunchIntent(context, intent.extras)
            └─► launch app's main launcher Activity
```

All Klaviyo extras (tracking data, button properties) are copied forward into the destination
intent so they are available in the host Activity's `onCreate`/`onNewIntent` if needed.

---

## Notification intent building

### Body tap (`makeOpenedIntent`)

When the flag is enabled, the body tap `PendingIntent` targets the trampoline:

```kotlin
Intent(context, KlaviyoTrampolineActivity::class.java).apply {
    message.deepLink?.let { data = it }   // deep link URI if present
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appendKlaviyoExtras(message)           // tracking payload
}
```

When the flag is disabled (default), behavior is unchanged: the `PendingIntent` targets the
host app's launcher or deep link Activity directly.

### Action button taps (`makeTrampolineIntentForButton`)

Action button taps are also routed through the trampoline when the flag is enabled:

```kotlin
Intent(context, KlaviyoTrampolineActivity::class.java).apply {
    if (button is ActionButton.DeepLink) data = button.url.toUri()
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    putExtra(Constants.NOTIFICATION_TAG_EXTRA, notificationTag)  // for dismissal
    appendKlaviyoExtras(message)           // tracking payload
    appendActionButtonExtras(button)       // Button ID, Label, Action, Link
}
```

The `NOTIFICATION_TAG_EXTRA` is required because Android's `setAutoCancel(true)` only fires
on body taps — action button taps do not auto-dismiss the notification. The trampoline calls
`Klaviyo.handlePush(intent)` which reads this tag and cancels the notification via
`NotificationManagerCompat`.

---

## Dedup guard

Integrators migrating from manual tracking may enable the flag without immediately removing
their `Klaviyo.handlePush(intent)` call. Without protection, this would produce two
`Opened Push` events per tap.

The guard works via an intent extra:

1. The trampoline calls `Klaviyo.handlePush(intent)` — tracking fires
2. The trampoline stamps `_klaviyo.auto_tracked = true` on the intent
3. The destination intent copies these extras forward (via `copyIntent` / `intent.extras`)
4. When the host Activity calls `handlePush(intent)`, the existing guard in `Klaviyo.handlePush`
   checks for `AUTO_TRACKED_EXTRA` and short-circuits — no duplicate event, no duplicate
   `DeepLinkHandler` dispatch

```kotlin
// In Klaviyo.handlePush:
?.takeIf {
    val alreadyAutoTracked = intent?.getBooleanExtra(AUTO_TRACKED_EXTRA, false) == true
    !alreadyAutoTracked  // skip if trampoline already handled this tap
}
```

---

## Migration path for integrators

| Scenario | Required changes |
|----------|-----------------|
| New integration | Add manifest flag. No `handlePush` call needed anywhere. |
| Existing integration, clean migration | Add manifest flag + remove `handlePush` call from Activities. |
| Existing integration, gradual migration | Add manifest flag only — dedup guard prevents double-tracking. Remove `handlePush` calls at your own pace. |

---

## What is NOT changed

- Push token registration — unaffected
- Notification display logic — unaffected
- The `handlePush` public API — still works identically when called manually
- Behavior when the flag is `false` (default) — byte-for-byte identical to the previous SDK

---

## Key constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `METADATA_AUTOMATIC_PUSH_OPEN_TRACKING` | `com.klaviyo.push.automatic_open_tracking` | Manifest opt-in key |
| `AUTO_TRACKED_EXTRA` | `_klaviyo.auto_tracked` | Dedup flag stamped by trampoline |
| `NOTIFICATION_TAG_EXTRA` | `_klaviyo.notification_tag` | Used to dismiss notification on action button tap |

The `_klaviyo.` internal prefix ensures these extras are never forwarded to the Klaviyo API
as event properties.

---

## Files changed

| File | Change |
|------|--------|
| `sdk/push-fcm/src/main/AndroidManifest.xml` | Declares `KlaviyoTrampolineActivity` |
| `sdk/push-fcm/src/main/java/…/KlaviyoTrampolineActivity.kt` | New — trampoline implementation |
| `sdk/push-fcm/src/main/java/…/KlaviyoNotification.kt` | Routes body tap + action button intents through trampoline when flag enabled |
| `sdk/push-fcm/src/main/java/…/KlaviyoPushService.kt` | Adds `METADATA_AUTOMATIC_PUSH_OPEN_TRACKING` constant |
| `sdk/analytics/src/main/java/…/Klaviyo.kt` | Adds dedup guard in `handlePush` |
| `sdk/core/src/main/java/…/Constants.kt` | Adds `AUTO_TRACKED_EXTRA` |
| `sdk/core/src/main/java/…/Config.kt` | Adds `getManifestBoolean` to interface |
| `sdk/core/src/main/java/…/KlaviyoConfig.kt` | Implements `getManifestBoolean` |
| `sample/src/main/AndroidManifest.xml` | Opts sample app into auto-tracking |
| `sample/src/main/java/…/SampleActivity.kt` | Removes manual `handlePush` call |
