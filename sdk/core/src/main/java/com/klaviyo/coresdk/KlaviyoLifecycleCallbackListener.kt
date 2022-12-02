package com.klaviyo.coresdk

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.klaviyo.coresdk.networking.NetworkBatcher

/**
 * Custom lifecycle callbacks used to track a user's activity in the parent application
 */
class KlaviyoLifecycleCallbackListener : Application.ActivityLifecycleCallbacks {
    private var activitiesActive = 0

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
        activitiesActive++
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
        activitiesActive--
        if (activitiesActive == 0) {
            NetworkBatcher.forceEmptyQueue()
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
    }
}
