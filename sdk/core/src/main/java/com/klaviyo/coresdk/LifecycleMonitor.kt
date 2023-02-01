package com.klaviyo.coresdk

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

typealias ActivityObserver = (activity: Activity) -> Unit

/**
 * Provides methods to react to changes in the application environment
 */
interface LifecycleMonitor {

    /**
     * Register an observer to be notified when all application activities stopped
     *
     * @param observer
     */
    fun whenStopped(observer: ActivityObserver)
}

/**
 * Service for monitoring the application lifecycle and network connectivity
 */
internal object KlaviyoLifecycleMonitor : LifecycleMonitor, ActivityLifecycleCallbacks {

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
