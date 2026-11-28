package com.klaviyo.inbox

import com.klaviyo.core.Registry
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A [ResetMarkerStore] in memory, so a test can restart a gate over the same pending generation and
 * make a write fail. Compiled into both the unit and instrumented test source sets.
 */
internal class InMemoryResetMarkerStore(var persisted: Long? = null) : ResetMarkerStore {
    val commits = mutableListOf<Long?>()
    var commitSucceeds = true
    var loadFailure: Exception? = null
    var loads = 0
        private set

    override fun load(): Long? {
        loads += 1
        loadFailure?.let { throw it }
        return persisted
    }

    override fun commit(generation: Long?): Boolean {
        commits += generation
        if (!commitSucceeds) return false
        persisted = generation
        return true
    }
}

/**
 * A gate over [dataSource] that records markers in memory and drains on [dispatcher].
 */
internal fun inboxResetGate(
    dataSource: LocalInboxDataSource,
    markers: ResetMarkerStore = InMemoryResetMarkerStore(),
    dispatcher: CoroutineDispatcher = Registry.dispatcher
) = InboxResetGate(dataSource, markers, dispatcher)

/**
 * A repository over [dataSource] and a gate with the given [markers].
 */
internal fun gatedRepository(
    dataSource: LocalInboxDataSource,
    markers: ResetMarkerStore = InMemoryResetMarkerStore(),
    dispatcher: CoroutineDispatcher = Registry.dispatcher,
    retentionLimit: Int = KlaviyoInboxRepository.DEFAULT_RETENTION_LIMIT
) = KlaviyoInboxRepository(
    dataSource,
    inboxResetGate(dataSource, markers, dispatcher),
    retentionLimit
)

/**
 * A repository that resolves [gate], as the provider's registered repository resolves the
 * registered gate.
 */
internal fun gatedRepository(
    dataSource: LocalInboxDataSource,
    gate: () -> InboxResetGate,
    retentionLimit: Int = KlaviyoInboxRepository.DEFAULT_RETENTION_LIMIT
) = KlaviyoInboxRepository(dataSource, gate, retentionLimit)
