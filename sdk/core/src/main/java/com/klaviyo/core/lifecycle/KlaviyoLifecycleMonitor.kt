package com.klaviyo.core.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Service for monitoring the application lifecycle and network connectivity
 */
internal object KlaviyoLifecycleMonitor : LifecycleMonitor, Application.ActivityLifecycleCallbacks {

    private var activeActivities = 0

    private var activityObservers = mutableListOf<ActivityObserver>()

    override fun onActivityEvent(observer: ActivityObserver) {
        activityObservers += observer
    }

    override fun offActivityEvent(observer: ActivityObserver) {
        activityObservers -= observer
    }

    private fun invokeObservers(event: ActivityEvent) = activityObservers.forEach { it(event) }

    //region ActivityLifecycleCallbacks

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        invokeObservers(ActivityEvent.Created(activity, bundle))
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivities++
        invokeObservers(ActivityEvent.Started(activity))
    }

    override fun onActivityResumed(activity: Activity) {
        invokeObservers(ActivityEvent.Resumed(activity))
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        invokeObservers(ActivityEvent.SaveInstanceState(activity, bundle))
    }

    override fun onActivityPaused(activity: Activity) {
        invokeObservers(ActivityEvent.Paused(activity))
    }

    override fun onActivityStopped(activity: Activity) {
        if (activeActivities == 0) return

        activeActivities--
        invokeObservers(ActivityEvent.Stopped(activity))

        if (activeActivities == 0) {
            invokeObservers(ActivityEvent.AllStopped())
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        invokeObservers(ActivityEvent.Destroyed(activity))
    }

    //endregion
}
