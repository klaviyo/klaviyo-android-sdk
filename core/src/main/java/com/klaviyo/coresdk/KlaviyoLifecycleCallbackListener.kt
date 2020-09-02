package com.klaviyo.coresdk

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.klaviyo.coresdk.networking.NetworkBatcher

class KlaviyoLifecycleCallbackListener: Application.ActivityLifecycleCallbacks {
    private var activitiesActive = 0

    override fun onActivityPaused(p0: Activity) {
    }

    override fun onActivityStarted(p0: Activity) {
    }

    override fun onActivityDestroyed(p0: Activity) {
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
    }

    override fun onActivityStopped(p0: Activity) {
        activitiesActive--
        if (activitiesActive == 0) {
            NetworkBatcher.forceEmptyQueue()
        }
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {
        activitiesActive++
    }

    override fun onActivityResumed(p0: Activity) {
    }

}