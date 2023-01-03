//[core](../../../index.md)/[com.klaviyo.coresdk](../index.md)/[KlaviyoLifecycleCallbackListener](index.md)

# KlaviyoLifecycleCallbackListener

[androidJvm]\
class [KlaviyoLifecycleCallbackListener](index.md) : [Application.ActivityLifecycleCallbacks](https://developer.android.com/reference/kotlin/android/app/Application.ActivityLifecycleCallbacks.html)

Custom lifecycle callbacks used to track a user's activity in the parent application

## Constructors

| | |
|---|---|
| [KlaviyoLifecycleCallbackListener](-klaviyo-lifecycle-callback-listener.md) | [androidJvm]<br>fun [KlaviyoLifecycleCallbackListener](-klaviyo-lifecycle-callback-listener.md)() |

## Functions

| Name | Summary |
|---|---|
| [onActivityCreated](on-activity-created.md) | [androidJvm]<br>open override fun [onActivityCreated](on-activity-created.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), bundle: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)?) |
| [onActivityDestroyed](on-activity-destroyed.md) | [androidJvm]<br>open override fun [onActivityDestroyed](on-activity-destroyed.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPaused](on-activity-paused.md) | [androidJvm]<br>open override fun [onActivityPaused](on-activity-paused.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPostCreated](index.md#207581428%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPostCreated](index.md#207581428%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), p1: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)?) |
| [onActivityPostDestroyed](index.md#-889937112%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPostDestroyed](index.md#-889937112%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPostPaused](index.md#45748501%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPostPaused](index.md#45748501%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPostResumed](index.md#-1071244310%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPostResumed](index.md#-1071244310%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPostSaveInstanceState](index.md#2121633432%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPostSaveInstanceState](index.md#2121633432%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), p1: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)) |
| [onActivityPostStarted](index.md#2066048384%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPostStarted](index.md#2066048384%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPostStopped](index.md#-869351500%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPostStopped](index.md#-869351500%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPreCreated](index.md#-232817233%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPreCreated](index.md#-232817233%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), p1: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)?) |
| [onActivityPreDestroyed](index.md#651230477%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPreDestroyed](index.md#651230477%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPrePaused](index.md#-240809648%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPrePaused](index.md#-240809648%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPreResumed](index.md#-1364612337%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPreResumed](index.md#-1364612337%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPreSaveInstanceState](index.md#-207051715%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPreSaveInstanceState](index.md#-207051715%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), p1: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)) |
| [onActivityPreStarted](index.md#1772680357%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPreStarted](index.md#1772680357%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityPreStopped](index.md#-1162719527%2FFunctions%2F2074384153) | [androidJvm]<br>open fun [onActivityPreStopped](index.md#-1162719527%2FFunctions%2F2074384153)(p0: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityResumed](on-activity-resumed.md) | [androidJvm]<br>open override fun [onActivityResumed](on-activity-resumed.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivitySaveInstanceState](on-activity-save-instance-state.md) | [androidJvm]<br>open override fun [onActivitySaveInstanceState](on-activity-save-instance-state.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html), bundle: [Bundle](https://developer.android.com/reference/kotlin/android/os/Bundle.html)) |
| [onActivityStarted](on-activity-started.md) | [androidJvm]<br>open override fun [onActivityStarted](on-activity-started.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
| [onActivityStopped](on-activity-stopped.md) | [androidJvm]<br>open override fun [onActivityStopped](on-activity-stopped.md)(activity: [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html)) |
