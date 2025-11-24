# Geofencing WorkManager Implementation - Context Document

## Problem Statement

When geofence transitions occur while the app is backgrounded or the device is in Doze mode, the API requests to send geofence events are created and enqueued but never actually sent until the app is foregrounded. This can result in delays of hours or even days.

### Root Cause

1. **Doze Mode Restrictions**: During Doze mode, Android suspends network access for apps
2. **Network Check Fails**: `Registry.networkMonitor.isNetworkConnected()` returns `false` during Doze
3. **Queue Stalls**: When `KlaviyoApiRequest.send()` checks network availability (line 309), it returns early with `Status.Unsent`
4. **No Retry Trigger**: The queue only processes again when:
   - Network change event fires (doesn't happen during Doze)
   - App is foregrounded by user
5. **Result**: Geofence events sit in the persistent queue indefinitely until user opens the app

### Current Flow (Broken)

```
Geofence Transition (during Doze)
  ↓
BroadcastReceiver.onReceive() via goAsync()
  ↓
createGeofenceEvent() → enqueues to API queue
  ↓
Triggers startBatch() → NetworkRunnable
  ↓
isNetworkConnected() returns false → exits early
  ↓
Request sits in queue forever (until app foregrounded)
```

## Research Summary

### Android Doze Mode Behavior

From official Android documentation and Stack Overflow research:

- **Network Access**: Completely suspended during Doze (not just throttled)
- **BroadcastReceivers**: Can still receive geofence intents, but network operations fail with socket errors
- **WorkManager/JobScheduler**: Also suspended during deep Doze
- **Maintenance Windows**: Android periodically (every ~30-60 min) exits Doze briefly to let apps catch up
- **Expedited Work**: WorkManager 2.7+ offers `setExpedited()` which:
  - Runs immediately when app is foregrounded
  - Runs immediately when backgrounded if quota available
  - Defers to maintenance window when in deep Doze
  - Uses expedited jobs on Android 12+, foreground service on Android 11-

### Key Insight

**WorkManager doesn't bypass Doze entirely, but it respects maintenance windows**. This means:
- Immediate execution when app is active ✅
- Deferred execution during Doze (but guaranteed eventually) ✅
- Better than our current approach (stuck forever) ✅

## Solutions Considered

### Option A: Fix IOException Handling Only (Rejected)

**What**: Change error handling so IOException during Doze returns `PendingRetry` instead of `Failed`

**Problem**: Doesn't solve the core issue - requests still never attempted during Doze because `isNetworkConnected()` returns false before we even try to send

**Status**: We're fixing this separately in another worktree (see `klaviyo-android-sdk-fix-request-error-handling`), but it doesn't solve the geofencing problem

### Option B: WorkManager Replaces Entire Queue (Rejected)

**What**: Bypass `KlaviyoApiClient` entirely, send geofence events directly via WorkManager

**Pros**:
- Clean separation
- No threading redundancy

**Cons**:
- Loses batching efficiency
- Loses existing retry/persistence infrastructure
- Different code path than all other events
- Would need to reimplement all the queue logic

### Option C: WorkManager as "Wake-Up Call" (CHOSEN) ✅

**What**: Use WorkManager to trigger the existing API queue flush during maintenance windows

**How it works**:
```
Geofence Transition (during Doze)
  ↓
createGeofenceEvent() → enqueues to API queue
  ↓
Check if network available:
  - YES: Normal flow (immediate send via HandlerThread)
  - NO: Schedule expedited WorkManager job
  ↓
WorkManager waits for constraints (network + maintenance window)
  ↓
Worker executes: calls KlaviyoApiClient.startBatch(force=true)
  ↓
Existing HandlerThread/queue logic processes all pending requests
```

**Pros**:
- Minimal changes to existing architecture
- Leverages all existing queue/retry/batching logic
- WorkManager is just a scheduler, HandlerThread is still the executor
- Works for ALL queued requests (not just geofence events)
- Solves the Doze problem completely

**Cons**:
- Adds WorkManager dependency (~200KB)
- Two systems involved (but they serve different purposes)

## Architecture: Why This Isn't Redundant

**Concern**: WorkManager runs on background thread, then triggers HandlerThread. Isn't that two layers of threading?

**Answer**: No - they serve different purposes:

### WorkManager's Job (Scheduler):
- **When**: Decides when to execute based on Android constraints (network, battery, Doze)
- **Lifecycle**: Survives process death, app restarts
- **System Integration**: Respects Doze mode, maintenance windows, battery optimization
- **Guarantees**: "This will execute eventually when conditions are right"

### HandlerThread's Job (Executor):
- **Batching**: Combines multiple requests into efficient batches
- **Serial Processing**: Sends requests one at a time in order
- **Backoff**: Implements exponential backoff on retries
- **Throttling**: Controls flush intervals based on network type

**Analogy**: WorkManager is the alarm clock that wakes you up at the right time. HandlerThread is your morning routine once you're awake. You don't skip your routine just because an alarm woke you up.

## Implementation Plan

### 1. Add WorkManager Dependency

**File**: `sdk/location/build.gradle`

```gradle
dependencies {
    // ... existing dependencies
    implementation "androidx.work:work-runtime-ktx:2.9.0"
}
```

### 2. Create QueueFlushWorker

**File**: `sdk/location/src/main/java/com/klaviyo/location/QueueFlushWorker.kt`

```kotlin
package com.klaviyo.location

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry

/**
 * WorkManager worker that triggers the API queue flush
 *
 * This is scheduled when geofence events are created during Doze mode.
 * WorkManager ensures this executes during maintenance windows when network
 * access is available.
 */
internal class QueueFlushWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Registry.log.debug("WorkManager triggered queue flush")

        // Trigger the existing queue flush logic
        // This will process all pending requests, not just geofence events
        Registry.get<ApiClient>().startBatch(force = true)

        return Result.success()
    }
}
```

### 3. Add WorkManager Scheduling Method

**File**: `sdk/location/src/main/java/com/klaviyo/location/KlaviyoLocationManager.kt`

Add this method to `KlaviyoLocationManager` class:

```kotlin
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * Schedule a WorkManager job to flush the API queue when network becomes available
 *
 * This is used when geofence events are created during Doze mode.
 * WorkManager will execute during the next maintenance window.
 */
private fun scheduleWorkManagerFlush() {
    val workRequest = OneTimeWorkRequestBuilder<QueueFlushWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()

    WorkManager.getInstance(Registry.config.applicationContext)
        .enqueueUniqueWork(
            "klaviyo_queue_flush",
            ExistingWorkPolicy.KEEP, // Don't schedule multiple times
            workRequest
        )

    Registry.log.debug("Scheduled WorkManager queue flush")
}
```

### 4. Modify Geofence Event Creation

**File**: `sdk/location/src/main/java/com/klaviyo/location/KlaviyoLocationManager.kt`

Modify the `handleGeofenceIntent()` method around line 350-374:

**Before**:
```kotlin
val requestUuids = geofencingEvent.triggeringGeofences?.map { it.toKlaviyoGeofence() }
    ?.mapNotNull { kGeofence ->
        // ... validation and cooldown checks ...

        // Create the event and record the transition time
        cooldownTracker.recordTransition(kGeofence.id, geofenceTransition)
        createGeofenceEvent(geofenceTransition, kGeofence)
    } ?: emptyList()
```

**After**:
```kotlin
val requestUuids = geofencingEvent.triggeringGeofences?.map { it.toKlaviyoGeofence() }
    ?.mapNotNull { kGeofence ->
        // ... validation and cooldown checks ...

        // Create the event and record the transition time
        cooldownTracker.recordTransition(kGeofence.id, geofenceTransition)
        createGeofenceEvent(geofenceTransition, kGeofence)
    } ?: emptyList()

// If network is unavailable (Doze mode), schedule WorkManager to flush queue later
if (!Registry.networkMonitor.isNetworkConnected() && requestUuids.isNotEmpty()) {
    scheduleWorkManagerFlush()
}
```

**Key point**: We only schedule WorkManager if network is unavailable. Otherwise, the normal immediate send flow happens via the existing HandlerThread logic.

## Expected Behavior

### Scenario 1: Geofence Triggered While App Foregrounded
```
Trigger → createEvent() → isNetworkConnected() = true → immediate send ✅
```
**Result**: Event sent within seconds (existing behavior)

### Scenario 2: Geofence Triggered While Backgrounded (Not in Doze)
```
Trigger → createEvent() → isNetworkConnected() = true → immediate send ✅
```
**Result**: Event sent within seconds (existing behavior)

### Scenario 3: Geofence Triggered During Deep Doze
```
Trigger → createEvent() → isNetworkConnected() = false → schedule WorkManager
  ↓
WorkManager waits for maintenance window (~30-60 min)
  ↓
Worker executes → startBatch(force=true) → sends all pending events ✅
```
**Result**: Event sent within 30-60 minutes (NEW behavior - huge improvement)

### Scenario 4: Multiple Geofences During Doze
```
Geofence 1 → enqueued → schedule WorkManager
Geofence 2 → enqueued → KEEP policy = no duplicate work
Geofence 3 → enqueued → KEEP policy = no duplicate work
  ↓
Single WorkManager job executes → sends all 3 events in batch ✅
```
**Result**: Efficient batching still preserved

### Scenario 5: Screen Wakes / Charging During Doze
```
Device exits Doze → WorkManager maintenance window → sends immediately ✅
```
**Result**: Events sent as soon as possible after conditions improve

## Testing Requirements

### Unit Tests

1. **Test WorkManager is NOT scheduled when network available**:
   - Mock `isNetworkConnected()` to return `true`
   - Create geofence event
   - Verify WorkManager not enqueued

2. **Test WorkManager IS scheduled when network unavailable**:
   - Mock `isNetworkConnected()` to return `false`
   - Create geofence event
   - Verify WorkManager enqueued with correct constraints

3. **Test QueueFlushWorker calls startBatch()**:
   - Mock ApiClient
   - Execute worker
   - Verify `startBatch(force=true)` was called

### Integration Tests

1. **Test geofence event eventually sends during simulated Doze**:
   - Trigger geofence while network "unavailable"
   - Simulate maintenance window (make network "available")
   - Verify WorkManager executes and event is sent

2. **Test multiple geofence events batch correctly**:
   - Create multiple events while network unavailable
   - Verify only one WorkManager job scheduled (KEEP policy)
   - Verify all events sent when job executes

### Manual Testing

1. **Real device Doze mode testing**:
   - Enable Doze mode via adb: `adb shell dumpsys deviceidle force-idle`
   - Trigger geofence transition
   - Exit Doze: `adb shell dumpsys deviceidle unforce`
   - Verify event appears in backend within minutes

2. **Background testing**:
   - Put app in background
   - Trigger geofence
   - Don't open app
   - Wait 30-60 minutes
   - Check backend for event

## Edge Cases & Considerations

### 1. WorkManager Quota

**Issue**: Expedited work has quota limits when app is backgrounded

**Solution**: We use `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`. If quota exhausted, it runs as regular work (still executes during maintenance window)

### 2. Multiple Geofence Transitions

**Issue**: User could trigger many geofences while in Doze

**Solution**: `ExistingWorkPolicy.KEEP` prevents scheduling duplicate jobs. One worker executes and processes all pending events via the queue.

### 3. Process Death

**Issue**: App process might be killed after scheduling WorkManager

**Solution**: WorkManager persists scheduled work to disk. It will still execute even if process dies.

### 4. Network Flapping

**Issue**: Network might become available between event creation and WorkManager scheduling

**Solution**: Not a problem - the queue will process normally. WorkManager job might execute but find queue empty (harmless).

### 5. Existing Queue Items

**Issue**: Non-geofence requests might also be stuck in queue

**Solution**: **This is a feature, not a bug!** When WorkManager flushes the queue, it processes ALL pending requests (profiles, events, tokens). This solves background sending for the entire SDK.

## Related Work

### Separate Error Handling Fix

A separate agent is working in `klaviyo-android-sdk-fix-request-error-handling` worktree to fix:
- IOException handling (network errors should return `PendingRetry` not `Failed`)
- Unknown response codes (5xx should retry)

**That fix is orthogonal to this work** - it prevents data loss, but doesn't solve the Doze timing issue. Both fixes are needed.

### Future: Full HandlerThread → WorkManager Migration

There's interest in modernizing the entire `KlaviyoApiClient` to use WorkManager + Coroutines instead of HandlerThread. This would be a larger refactor that:
- Replaces all HandlerThread usage with WorkManager
- Uses Coroutines instead of callbacks
- Enables parallel request processing (configurable)

**This WorkManager-for-geofencing implementation is a first step** toward that larger modernization, but it's intentionally scoped to be safe and isolated.

## Success Criteria

✅ Geofence events created during Doze mode are sent within 30-60 minutes (not hours/days)

✅ Geofence events created while foregrounded are still sent immediately (< 5 seconds)

✅ No duplicate WorkManager jobs scheduled

✅ All existing tests still pass

✅ Geofencing works correctly on real devices in Doze mode

✅ No increase in battery drain or user-visible performance impact

## References

- [Android Doze Mode Documentation](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [WorkManager Documentation](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Expedited Work Guide](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#expedited)
- [Stack Overflow: Geofence BroadcastReceiver Network Issues](https://stackoverflow.com/questions/52257295/why-is-network-access-in-my-geofence-broadcast-receiver-unreliable)

## Implementation Checklist

- [ ] Add WorkManager dependency to `sdk/location/build.gradle`
- [ ] Create `QueueFlushWorker.kt`
- [ ] Add `scheduleWorkManagerFlush()` method to `KlaviyoLocationManager`
- [ ] Modify `handleGeofenceIntent()` to schedule WorkManager when network unavailable
- [ ] Add unit tests for WorkManager scheduling logic
- [ ] Add unit tests for `QueueFlushWorker`
- [ ] Add integration tests for end-to-end behavior
- [ ] Manual testing on real device with Doze mode
- [ ] Update documentation
- [ ] Run full test suite: `./gradlew :sdk:location:testDebugUnitTest`
- [ ] Run lint: `./gradlew ktlintFormat`
- [ ] Create PR with detailed description
