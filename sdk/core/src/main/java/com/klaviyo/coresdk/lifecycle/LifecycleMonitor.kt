package com.klaviyo.coresdk.lifecycle

import android.app.Activity

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
    fun onAllActivitiesStopped(observer: ActivityObserver)

    // TODO removal of listeners?
}
