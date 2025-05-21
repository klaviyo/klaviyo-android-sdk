package com.klaviyo.core.lifecycle

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Bundle
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.AdvancedAPI
import com.klaviyo.core.utils.WeakReferenceDelegate
import java.util.Collections

/**
 * Service for monitoring the application lifecycle and network connectivity
 */
internal object KlaviyoLifecycleMonitor : LifecycleMonitor, Application.ActivityLifecycleCallbacks, ComponentCallbacks {

    private var activeActivities = 0

    private val activityObservers = Collections.synchronizedList(
        mutableListOf<ActivityObserver>()
    )

    override var currentActivity: Activity? by WeakReferenceDelegate()
        private set

    @AdvancedAPI
    override fun assignCurrentActivity(activity: Activity) {
        if (activity != currentActivity) {
            // If we missed this activity's creation, then we need to increment the count now
            activeActivities++
            currentActivity = activity
        }
    }

    override fun onActivityEvent(observer: ActivityObserver) {
        activityObservers += observer
    }

    override fun offActivityEvent(observer: ActivityObserver) {
        activityObservers -= observer
    }

    private fun broadcastEvent(event: ActivityEvent) {
        Registry.log.verbose(event.type)
        synchronized(activityObservers) {
            activityObservers.forEach { it(event) }
        }
    }

    //region ActivityLifecycleCallbacks

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        broadcastEvent(ActivityEvent.Created(activity, bundle))
    }

    override fun onActivityStarted(activity: Activity) {
        if (activeActivities == 0) {
            broadcastEvent(ActivityEvent.FirstStarted(activity))
        }

        activeActivities++
        broadcastEvent(ActivityEvent.Started(activity))
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        broadcastEvent(ActivityEvent.Resumed(activity))
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        broadcastEvent(ActivityEvent.SaveInstanceState(activity, bundle))
    }

    override fun onActivityPaused(activity: Activity) {
        checkActivityClear(activity)
        broadcastEvent(ActivityEvent.Paused(activity))
    }

    private fun checkActivityClear(activity: Activity) {
        if (activity == currentActivity) {
            currentActivity = null
        }
    }

    override fun onActivityStopped(activity: Activity) {
        checkActivityClear(activity)
        if (activeActivities == 0) return

        activeActivities--
        broadcastEvent(ActivityEvent.Stopped(activity))

        if (activeActivities == 0) {
            broadcastEvent(ActivityEvent.AllStopped())
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        // Warning: onActivityDestroyed is unreliable, I'm not even going to try to broadcast it
    }

    //region ComponentCallbacks

    override fun onConfigurationChanged(newConfig: Configuration) {
        broadcastEvent(ActivityEvent.ConfigurationChanged(newConfig))
    }

    override fun onLowMemory() {
        // currently not needed
    }

    //endregion
}
