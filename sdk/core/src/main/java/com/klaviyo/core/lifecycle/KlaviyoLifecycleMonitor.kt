package com.klaviyo.core.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.klaviyo.core.Registry

/**
 * Service for monitoring the application lifecycle and network connectivity
 */
internal object KlaviyoLifecycleMonitor : LifecycleMonitor, Application.ActivityLifecycleCallbacks {

    private var activeActivities = 0

    private var activityObservers = mutableListOf<ActivityObserver>()

    init {
        onActivityEvent { Registry.log.debug(it.type) }
    }

    override fun onActivityEvent(observer: ActivityObserver) {
        activityObservers += observer
    }

    override fun offActivityEvent(observer: ActivityObserver) {
        activityObservers -= observer
    }

    private fun broadcastEvent(event: ActivityEvent) = activityObservers.forEach { it(event) }

    //region ActivityLifecycleCallbacks

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        broadcastEvent(ActivityEvent.Created(activity, bundle))
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivities++
        broadcastEvent(ActivityEvent.Started(activity))
    }

    override fun onActivityResumed(activity: Activity) {
        broadcastEvent(ActivityEvent.Resumed(activity))
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        broadcastEvent(ActivityEvent.SaveInstanceState(activity, bundle))
    }

    override fun onActivityPaused(activity: Activity) {
        broadcastEvent(ActivityEvent.Paused(activity))
    }

    override fun onActivityStopped(activity: Activity) {
        if (activeActivities == 0) return

        activeActivities--
        broadcastEvent(ActivityEvent.Stopped(activity))

        if (activeActivities == 0) {
            broadcastEvent(ActivityEvent.AllStopped())
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        broadcastEvent(ActivityEvent.Destroyed(activity))
    }

    //endregion
}
