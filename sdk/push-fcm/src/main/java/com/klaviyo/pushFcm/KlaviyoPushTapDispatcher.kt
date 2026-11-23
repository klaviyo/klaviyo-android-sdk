package com.klaviyo.pushFcm

import android.content.Intent
import com.klaviyo.core.PushTapDestination
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.AdvancedAPI
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(AdvancedAPI::class)
internal class KlaviyoPushTapDispatcher : PushTapDispatcher {

    private val observers = CopyOnWriteArrayList<PushTapObserver>()

    override fun onPushTap(observer: PushTapObserver) {
        observers.addIfAbsent(observer)
    }

    override fun offPushTap(observer: PushTapObserver) {
        observers.remove(observer)
    }

    fun dispatch(intent: Intent, destination: PushTapDestination) {
        observers.forEach { observer ->
            try {
                observer.onPushTap(intent, destination)
            } catch (e: Throwable) {
                Registry.log.error("Push tap observer failed", e)
            }
        }
    }
}

/**
 * Dispatch to the registered [PushTapDispatcher], if it is push-fcm's own.
 * Dispatch stays off the public interface so only push-fcm can deliver taps to observers.
 */
@OptIn(AdvancedAPI::class)
internal fun dispatchToPushTapObservers(intent: Intent, destination: PushTapDestination) =
    (Registry.getOrNull<PushTapDispatcher>() as? KlaviyoPushTapDispatcher)
        ?.dispatch(intent, destination)
