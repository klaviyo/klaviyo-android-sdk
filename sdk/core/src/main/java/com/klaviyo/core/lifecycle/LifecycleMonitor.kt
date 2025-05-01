package com.klaviyo.core.lifecycle

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import com.klaviyo.core.utils.AdvancedAPI

typealias ActivityObserver = (activity: ActivityEvent) -> Unit

sealed class ActivityEvent(val activity: Activity? = null, val bundle: Bundle? = null) {

    val type: String get() = this.javaClass.simpleName

    class Created(activity: Activity, bundle: Bundle?) : ActivityEvent(activity, bundle)

    class Started(activity: Activity) : ActivityEvent(activity)

    class Resumed(activity: Activity) : ActivityEvent(activity)

    class SaveInstanceState(activity: Activity, bundle: Bundle) : ActivityEvent(activity, bundle)

    class Paused(activity: Activity) : ActivityEvent(activity)

    class Stopped(activity: Activity) : ActivityEvent(activity)

    class AllStopped : ActivityEvent()

    class ConfigurationChanged(val newConfig: Configuration) : ActivityEvent()
}

/**
 * Provides methods to react to changes in the application environment
 */
interface LifecycleMonitor {

    /**
     * Tracks the current activity of the host application.
     */
    val currentActivity: Activity?

    /**
     * Register an observer to be notified when all application activities stopped
     *
     * @param observer
     */
    fun onActivityEvent(observer: ActivityObserver)

    /**
     * De-register an observer from [onActivityEvent]
     *
     * @param observer
     */
    fun offActivityEvent(observer: ActivityObserver)

    /**
     * Explicitly sets the current activity.
     *
     * It is best to allow the SDK to track activities internally via [Application.ActivityLifecycleCallbacks].
     * However, this explicit override allows us to work around timing issues such as
     * when [LifecycleMonitor] can't be attached in time to capture the first Activity.
     *
     * @param activity
     */
    @AdvancedAPI
    fun assignCurrentActivity(activity: Activity)
}
