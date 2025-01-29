package com.klaviyo.messaging

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import com.klaviyo.core.Registry
import java.io.BufferedReader
import java.lang.ref.WeakReference

object InAppMessaging {

    private var currentActivityReference: WeakReference<Activity>? = null

    fun triggerInAppMessage() {
        getCurrentActivity()?.let { activity ->
            val rootView = activity.getRootViewGroup()
            val webView = KlaviyoWebView(activity)
            val html = activity
                .assets
                .open("IAMTest.html")
                .bufferedReader()
                .use(BufferedReader::readText)
                .replace(IAF_PUBLIC_KEY_PLACEHOLDER, Registry.config.apiKey)
                .replace(IAF_SDK_NAME_PLACEHOLDER, Registry.config.sdkName)
                .replace(IAF_SDK_VERSION_PLACEHOLDER, Registry.config.sdkVersion)

            webView.loadHtml(html)
            webView.addTo(rootView)
        }
    }

    private fun Activity.getRootViewGroup(): ViewGroup =
        window.decorView.findViewById(android.R.id.content)

    fun registerForInAppMessages(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                currentActivityReference = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        // todo this would be a good idea to add when we want to 'stop listening'. probably will attach to application ondestroy
        // application.unregisterActivityLifecycleCallbacks()
    }

    internal fun getCurrentActivity(): Activity? {
        return currentActivityReference?.get()
    }
}
