package com.klaviyo.inbox

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How many times this source's [RoomInboxDataSource.changes] emitted while [block] ran
 */
internal suspend fun RoomInboxDataSource.emissionsDuring(block: suspend () -> Unit): Int =
    coroutineScope {
        var emissions = 0
        val collector = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            changes.collect { emissions++ }
        }
        block()
        delay(EMISSIONS_SETTLE_MS)
        collector.cancel()
        emissions
    }

/**
 * How long to keep collecting after [block] returns, so a pending emission is observed
 */
internal const val EMISSIONS_SETTLE_MS = 100L
