package com.klaviyo.coresdk.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Service for monitoring the application lifecycle and network connectivity
 */
internal object KlaviyoLifecycleMonitor : LifecycleMonitor, Application.ActivityLifecycleCallbacks {

    private var activitiesActive = 0

    private var whenStopped = mutableListOf<ActivityObserver>()

    override fun whenStopped(observer: ActivityObserver) {
        whenStopped += observer
    }

    //region ActivityLifecycleCallbacks

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        activitiesActive++
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        activitiesActive--
        if (activitiesActive == 0) {
            whenStopped.forEach { it(activity) }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {}

    //endregion
}
