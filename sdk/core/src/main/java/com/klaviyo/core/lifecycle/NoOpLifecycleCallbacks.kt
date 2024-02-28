package com.klaviyo.core.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * A no-op implementation of ActivityLifecycleCallbacks
 * to temporarily replace the public property Klaviyo.lifecycleCallbacks
 * and prevent duplicate registration of the KlaviyoLifecycleMonitor
 * until next major release when we can make the breaking change to remove that public property
 */
object NoOpLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
