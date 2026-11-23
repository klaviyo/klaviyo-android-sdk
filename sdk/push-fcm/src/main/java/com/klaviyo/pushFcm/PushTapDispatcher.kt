package com.klaviyo.pushFcm

import android.content.Intent
import com.klaviyo.core.Constants
import com.klaviyo.core.PushTapDestination
import com.klaviyo.core.utils.AdvancedAPI

/**
 * Receives each Klaviyo notification tap handled by the push trampoline
 */
@AdvancedAPI
fun interface PushTapObserver {
    /**
     * Called on the main thread with the tapped notification [intent], carrying the push payload
     * as [Constants.PACKAGE_PREFIX]-prefixed extras, and its [destination]. An action button tap
     * also carries the button's id under [Constants.PACKAGE_PREFIX] +
     * [Constants.BUTTON_ID_PARAMETER].
     */
    fun onPushTap(intent: Intent, destination: PushTapDestination)
}

/**
 * Lets other modules and wrapper SDKs observe Klaviyo notification taps, without push-fcm
 * depending on them. Registered in [com.klaviyo.core.Registry] at process start.
 *
 * The trampoline dispatches every tap it handles, after the open is tracked and before it
 * dispatches to the [PushTapDestination], so this sees taps that never reach the host activity,
 * such as [PushTapDestination.External]. For [PushTapDestination.DeepLink] and
 * [PushTapDestination.OpenApp] the host activity still launches with the push payload, so an
 * observer that already reads the activity intent should ignore those to avoid emitting the same
 * tap twice.
 *
 * Observers run synchronously on the main thread before the destination starts, so they must
 * return quickly. An observer that throws is logged and skipped, and never stops the other
 * observers or the dispatch.
 */
@AdvancedAPI
interface PushTapDispatcher {
    fun onPushTap(observer: PushTapObserver)

    fun offPushTap(observer: PushTapObserver)
}
