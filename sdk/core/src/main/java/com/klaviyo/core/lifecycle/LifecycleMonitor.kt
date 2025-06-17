package com.klaviyo.core.lifecycle

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import com.klaviyo.core.utils.AdvancedAPI

typealias ActivityObserver = (activity: ActivityEvent) -> Unit

/**
 * Represent different events emitted in response to lifecycle triggers from the host application
 */
sealed class ActivityEvent(open val activity: Activity? = null, val bundle: Bundle? = null) {

    /**
     * Get the type of the event as a string (e.g. for logging)
     */
    val type: String get() = this.javaClass.simpleName

    /**
     * Emitted when [Activity.onCreate] is called from an activity within the host app
     */
    class Created(override val activity: Activity, bundle: Bundle?) : ActivityEvent(
        activity,
        bundle
    )

    /**
     * Emitted when [Activity.onStart] is called from an activity within the host app
     */
    class Started(override val activity: Activity) : ActivityEvent(activity)

    /**
     * Emitted when the host application moves to the foreground
     * i.e. an activity [Started], and the application transitions from 0 to 1 started activity
     */
    class FirstStarted(override val activity: Activity) : ActivityEvent(activity)

    /**
     * Emitted when [Activity.onResume] is called from an activity within the host app
     */
    class Resumed(override val activity: Activity) : ActivityEvent(activity)

    /**
     * Emitted when [Activity.onSaveInstanceState] is called from an activity within the host app
     */
    class SaveInstanceState(override val activity: Activity, bundle: Bundle) : ActivityEvent(
        activity,
        bundle
    )

    /**
     * Emitted when [Activity.onPause] is called from an activity within the host app
     */
    class Paused(override val activity: Activity) : ActivityEvent(activity)

    /**
     * Emitted when [Activity.onStop] is called from an activity within the host app
     */
    class Stopped(override val activity: Activity) : ActivityEvent(activity)

    /**
     * Emitted when the host application moves to the background,
     * i.e. the last active activity [Stopped]
     */
    class AllStopped : ActivityEvent()

    /**
     * Emitted when [Activity.onConfigurationChanged] is called from an activity within the host app
     */
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
     * Intended for use in advanced scenarios where [LifecycleMonitor] cannot capture
     * an activity's [com.klaviyo.core.lifecycle.ActivityEvent.Started] event.
     *
     * Note: It is best to allow the SDK to track activities internally via [Application.ActivityLifecycleCallbacks].
     * However, this explicit override allows us to work around launch timing issues on certain platforms.
     *
     * See also: Klaviyo.registerForLifecycleCallbacks which allows for registering callbacks prior to initializing
     * which is typically a better workaround for launch timing issues.
     *
     * @param activity
     */
    @AdvancedAPI
    fun assignCurrentActivity(activity: Activity)
}
