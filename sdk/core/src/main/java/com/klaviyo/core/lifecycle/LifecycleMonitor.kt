package com.klaviyo.core.lifecycle

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.utils.AdvancedAPI
import com.klaviyo.core.utils.takeIf
import java.util.concurrent.atomic.AtomicBoolean

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

    /**
     * Helper function to run a task immediately if there is a current activity,
     * or wait for the next resumed activity if resumed within the optional timeout.
     *
     * A job that outlives its timeout is dropped. Callers that need a fallback should use the
     * [onTimeout] overload rather than relying on the resume arriving in time.
     *
     * @param timeout How long to wait for a resumed activity, or null to wait indefinitely
     * @param job Invoked with the current activity, or the next one to resume
     * @return null if [job] already ran against the current activity, otherwise a token that
     *  abandons the pending wait.
     */
    fun runWithCurrentOrNextActivity(
        timeout: Long? = null,
        job: (activity: Activity) -> Unit
    ): Clock.Cancellable? = runWithCurrentOrNextActivity(timeout, null, job)

    /**
     * Helper function to run a task immediately if there is a current activity, or wait for the
     * next resumed activity, falling back to [onTimeout] if none resumes within [timeout].
     *
     * Exactly one of [job] and [onTimeout] is invoked, unless the returned token cancels the wait
     * first. The timeout should be at least long enough to avoid race conditions between the
     * scheduling of the tasks, e.g. min [ACTIVITY_TRANSITION_GRACE_PERIOD] milliseconds.
     *
     * @param timeout How long to wait for a resumed activity, or null to wait indefinitely
     * @param onTimeout Invoked instead of [job] if [timeout] elapses with no resumed activity
     * @param job Invoked with the current activity, or the next one to resume
     * @return A token to cancel the pending wait or attempt to run immediately against the
     * current activity and abandon the wait, or null if [job] ran immediately.
     */
    fun runWithCurrentOrNextActivity(
        timeout: Long?,
        onTimeout: (() -> Unit)?,
        job: (activity: Activity) -> Unit
    ): Clock.Cancellable? {
        currentActivity?.let { activity ->
            job(activity)
            return null
        }

        // Track atomically whether the task or timeout have run
        val settled = AtomicBoolean(false)
        var observer: ActivityObserver? = null
        var timeoutTask: Clock.Cancellable? = null

        // Invoke the job when the next activity resumes, and cancel the timeout task
        val waitingTask: (activity: Activity, reason: String) -> Unit = { activity, reason ->
            if (settled.compareAndSet(false, true)) {
                Registry.log.verbose("Invoking postponed observer on $reason")
                observer?.let { offActivityEvent(it) }
                timeoutTask?.cancel()
                job(activity)
            } else {
                Registry.log.verbose("Postponed observer already settled, ignoring $reason")
            }
        }

        observer = { event ->
            event.takeIf<ActivityEvent.Resumed>()?.let { resumed ->
                waitingTask(resumed.activity, "resume")
            }
        }

        onActivityEvent(observer)

        // Cancel the task if a specified timeout elapses, and invoke the optional timeout callback
        timeoutTask = timeout?.let { delay ->
            Registry.clock.schedule(delay) {
                if (!settled.compareAndSet(false, true)) return@schedule
                Registry.log.verbose("Removing postponed observer after timeout ${delay}ms")
                offActivityEvent(observer)
                onTimeout?.invoke()
            }
        }

        return object : Clock.Cancellable {
            /**
             * Cancel the wait for the next resumed activity, and cancel the timeout task if any.
             * [onTimeout] is not invoked automatically, caller can do that manually if needed.
             */
            override fun cancel(): Boolean {
                if (!settled.compareAndSet(false, true)) return false
                Registry.log.verbose("Abandoning postponed observer")
                offActivityEvent(observer)
                timeoutTask?.cancel()
                return true
            }

            /**
             * Contract correctness: an option to attempt to invoke immediately. Not recommended.
             */
            override fun runNow() {
                currentActivity?.let { activity ->
                    waitingTask(activity, "runNow")
                } ?: run {
                    Registry.log.verbose("Postponed observer has no current activity, abandoning")
                    cancel()
                }
            }
        }
    }

    companion object {
        /**
         * Allow a brief grace period for events triggered by transitions between activities
         * In testing, this was rarely exceeds 10ms, allowing some extra time for safety.
         */
        const val ACTIVITY_TRANSITION_GRACE_PERIOD = 50L
    }
}
