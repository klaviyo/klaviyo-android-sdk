package com.klaviyo.inbox

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class InboxResetGateTest : BaseTest() {

    private val store = FakeInboxDataSource()
    private val markers = InMemoryResetMarkerStore()

    private fun TestScope.gated(
        inbox: FakeInboxDataSource = store,
        markerStore: InMemoryResetMarkerStore = markers
    ): Pair<InboxResetGate, InboxRepository> {
        val gate = InboxResetGate(inbox, markerStore, StandardTestDispatcher(testScheduler))
        return gate to KlaviyoInboxRepository(inbox, gate, RETENTION_LIMIT)
    }

    private fun message(id: String, companyId: String = API_KEY) =
        inboxMessageEntity(transmissionId = id, companyId = companyId)

    @Test
    fun `a reset clears messages captured under every company`() = runTest {
        val (gate, inbox) = gated()
        inbox.capture(message("a-1", "company-a"))
        inbox.capture(message("b-1", "company-b"))

        gate.requestReset()
        advanceUntilIdle()

        assertTrue(store.messages.isEmpty())
        assertEquals(listOf("insert", "insert", "clearAll"), store.calls)
        assertNull(markers.persisted)
    }

    @Test
    fun `a reset clears the store without an inbox call`() = runTest {
        val (gate, _) = gated()

        gate.requestReset()
        advanceUntilIdle()

        assertEquals(listOf("clearAll"), store.calls)
        assertNull(markers.persisted)
    }

    @Test
    fun `a restarted gate applies the persisted reset before a read`() = runTest {
        val markerStore = InMemoryResetMarkerStore(persisted = 5)
        store.messages["a-1"] = message("a-1")
        val (_, inbox) = gated(markerStore = markerStore)

        inbox.fetchPage(InboxQuery())

        assertEquals(listOf("clearAll", "fetchPage"), store.calls)
        assertTrue(store.messages.isEmpty())
        assertNull(markerStore.persisted)
    }

    @Test
    fun `a restarted gate applies the persisted reset before a capture`() = runTest {
        val markerStore = InMemoryResetMarkerStore(persisted = 5)
        val (_, inbox) = gated(markerStore = markerStore)

        assertEquals(InboxCaptureResult.CAPTURED, inbox.capture(message("a-1")))

        assertEquals(listOf("clearAll", "insert"), store.calls)
        assertNull(markerStore.persisted)
    }

    @Test
    fun `a failed clear keeps the reset pending and admits no operations`() = runTest {
        val (gate, inbox) = gated()
        store.clearFailure = IllegalStateException("disk full")

        gate.requestReset()
        advanceUntilIdle()

        assertEquals(1L, markers.persisted ?: -1L)
        assertEquals(InboxCaptureResult.FAILED, inbox.capture(message("a-1")))
        assertEquals(0, store.insertCount)

        val failure = runCatching { inbox.fetchPage(InboxQuery()) }.exceptionOrNull()
        assertTrue(failure is InboxException.StorageFailure)

        store.clearFailure = null
        assertEquals(InboxCaptureResult.CAPTURED, inbox.capture(message("a-1")))
        assertNull(markers.persisted)
        verify { spyLog.error(any(), any<IllegalStateException>()) }
    }

    @Test
    fun `acknowledging an older reset does not clear a newer one`() = runTest {
        val markerStore = InMemoryResetMarkerStore(persisted = 1)
        val (gate, inbox) = gated(markerStore = markerStore)
        val clearStarted = CompletableDeferred<Unit>()
        val finishClear = CompletableDeferred<Unit>()
        store.beforeClear = {
            if (store.clearAllCount == 1) {
                clearStarted.complete(Unit)
                finishClear.await()
            }
        }

        val read = async { inbox.fetchPage(InboxQuery()) }
        advanceUntilIdle()
        assertTrue(clearStarted.isCompleted)

        gate.requestReset()
        assertEquals(2L, markerStore.persisted ?: -1L)

        finishClear.complete(Unit)
        advanceUntilIdle()
        read.await()

        assertEquals(2, store.clearAllCount)
        assertEquals(listOf("clearAll", "clearAll", "fetchPage"), store.calls)
        assertEquals(listOf(2L, null), markerStore.commits)
        assertNull(markerStore.persisted)
    }

    @Test
    fun `a failed acknowledgement admits no captures`() = runTest {
        val (gate, inbox) = gated()

        gate.requestReset()
        markers.commitSucceeds = false
        advanceUntilIdle()

        assertEquals(InboxCaptureResult.FAILED, inbox.capture(message("a-1")))
        assertEquals(0, store.insertCount)
        assertEquals(1L, markers.persisted ?: -1L)

        markers.commitSucceeds = true
        assertEquals(InboxCaptureResult.CAPTURED, inbox.capture(message("a-1")))
        assertEquals(1, store.insertCount)
        assertNull(markers.persisted)
    }

    @Test
    fun `a reset that could not be recorded durably still blocks access`() = runTest {
        val (gate, inbox) = gated()
        markers.commitSucceeds = false

        gate.requestReset()
        assertNull(markers.persisted)
        advanceUntilIdle()

        assertEquals(InboxCaptureResult.FAILED, inbox.capture(message("a-1")))
        assertEquals(0, store.insertCount)

        markers.commitSucceeds = true
        assertEquals(InboxCaptureResult.CAPTURED, inbox.capture(message("a-1")))
        assertNull(markers.persisted)
    }

    @Test
    fun `resets requested together share one background clear`() = runTest {
        val (gate, _) = gated()

        gate.requestReset()
        gate.requestReset()
        advanceUntilIdle()

        assertEquals(listOf("clearAll"), store.calls)
        assertEquals(listOf(1L, 2L, null), markers.commits)
        assertNull(markers.persisted)
    }

    @Test
    fun `a marker that cannot be read blocks every operation`() = runTest {
        markers.loadFailure = IllegalStateException("preferences unavailable")
        val (_, inbox) = gated()

        assertEquals(InboxCaptureResult.FAILED, inbox.capture(message("a-1")))

        val failure = runCatching { inbox.fetchUnreadCount() }.exceptionOrNull()
        assertTrue(failure is InboxException.StorageFailure)
        assertEquals(0, store.insertCount)
    }

    @Test
    fun `a new reset recovers from a marker that could not be read`() = runTest {
        markers.loadFailure = IllegalStateException("preferences unavailable")
        val (gate, inbox) = gated()
        assertEquals(InboxCaptureResult.FAILED, inbox.capture(message("a-1")))

        markers.loadFailure = null
        gate.requestReset()
        advanceUntilIdle()

        assertEquals(InboxCaptureResult.CAPTURED, inbox.capture(message("a-1")))
        assertEquals(listOf("clearAll", "insert"), store.calls)
    }

    @Test
    fun `a recorded reset clears even while the marker stays unreadable`() = runTest {
        markers.loadFailure = IllegalStateException("preferences unavailable")
        val (gate, inbox) = gated()
        assertEquals(InboxCaptureResult.FAILED, inbox.capture(message("a-1")))

        gate.requestReset()
        advanceUntilIdle()
        assertEquals(listOf("clearAll"), store.calls)
        val loadsAfterReset = markers.loads

        assertEquals(InboxCaptureResult.CAPTURED, inbox.capture(message("a-1")))
        assertEquals(listOf("clearAll", "insert"), store.calls)
        assertEquals(loadsAfterReset, markers.loads)
    }

    @Test
    fun `a transient read failure recovers on a later gated call without a new reset`() = runTest {
        markers.loadFailure = IllegalStateException("preferences unavailable")
        val (_, inbox) = gated()
        assertEquals(InboxCaptureResult.FAILED, inbox.capture(message("a-1")))

        markers.loadFailure = null
        markers.persisted = 4
        assertEquals(InboxCaptureResult.CAPTURED, inbox.capture(message("a-1")))

        assertEquals(listOf("clearAll", "insert"), store.calls)
        assertNull(markers.persisted)
    }

    @Test
    fun `an acknowledgement failure is logged with its cause`() = runTest {
        val (gate, inbox) = gated()
        gate.requestReset()
        markers.commitSucceeds = false
        advanceUntilIdle()

        assertEquals(InboxCaptureResult.FAILED, inbox.capture(message("a-1")))
        verify(exactly = 2) {
            spyLog.error(
                any(),
                match<Exception> { it is InboxException.StorageFailure && it.cause != null }
            )
        }
    }

    private companion object {
        const val RETENTION_LIMIT = 100
    }
}

internal class FakeInboxDataSource : LocalInboxDataSource {
    val messages = mutableMapOf<String, InboxMessageEntity>()
    val calls = mutableListOf<String>()
    var insertCount = 0
    var clearAllCount = 0
    var clearFailure: Exception? = null
    var beforeClear: (suspend () -> Unit)? = null

    override val changes: Flow<Unit> = emptyFlow()

    override suspend fun insert(message: InboxMessageEntity, retentionLimit: Int): InboxInsertResult {
        calls += "insert"
        insertCount += 1
        return if (messages.putIfAbsent(message.transmissionId, message) == null) {
            InboxInsertResult.INSERTED
        } else {
            InboxInsertResult.DUPLICATE
        }
    }

    override suspend fun fetchPage(companyId: String, query: InboxRecordQuery): InboxRecordPage {
        calls += "fetchPage"
        return InboxRecordPage(emptyList(), hasMore = false)
    }

    override suspend fun countUnread(companyId: String): Int {
        calls += "countUnread"
        return 0
    }

    override suspend fun updateState(
        companyId: String,
        messageId: String,
        change: InboxStateChange,
        updatedAt: Long
    ): Boolean {
        calls += "updateState"
        return true
    }

    override suspend fun updateStateWhere(
        companyId: String,
        change: InboxStateChange,
        isRead: Boolean?,
        isArchived: Boolean?,
        updatedAt: Long
    ): Int {
        calls += "updateStateWhere"
        return 0
    }

    override suspend fun updateStateForIds(
        companyId: String,
        change: InboxStateChange,
        messageIds: List<String>,
        updatedAt: Long
    ): Int {
        calls += "updateStateForIds"
        return 0
    }

    override suspend fun clearByCompanyId(companyId: String): Int {
        calls += "clearByCompanyId"
        val originalCount = messages.size
        messages.values.removeAll { it.companyId == companyId }
        return originalCount - messages.size
    }

    override suspend fun clearAll() {
        calls += "clearAll"
        clearAllCount += 1
        beforeClear?.invoke()
        clearFailure?.let { throw it }
        messages.clear()
    }
}
