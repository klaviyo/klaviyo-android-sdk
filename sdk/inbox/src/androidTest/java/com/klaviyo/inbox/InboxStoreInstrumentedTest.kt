package com.klaviyo.inbox

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InboxStoreInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "inbox-test-${UUID.randomUUID()}.db"
    private val openDatabases = mutableListOf<InboxDatabase>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InboxDatabase::class.java
    )

    @Before
    fun setup() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanup() {
        openSources.forEach { it.close() }
        openDatabases.forEach { it.close() }
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(): InboxDatabase = Room.databaseBuilder(
        context,
        InboxDatabase::class.java,
        databaseName
    ).build().also { openDatabases += it }

    private val dao get() = openDatabase().messageDao()

    private val openSources = mutableListOf<RoomInboxDataSource>()

    private fun openSource() = RoomInboxDataSource(context, databaseName).also { openSources += it }

    private fun message(
        transmissionId: String,
        companyId: String = COMPANY,
        receivedAt: Long = 1_000L,
        sentAt: Long = receivedAt
    ) = inboxMessageEntity(
        transmissionId = transmissionId,
        companyId = companyId,
        body = "Body $transmissionId",
        defaultDestination = InboxRecordDestination("deep_link", "myapp://sale"),
        mediaUrl = "https://cdn.example.com/$transmissionId.png",
        mediaType = "png",
        customData = mapOf("promo" to "SALE20", "tier" to "gold"),
        actions = listOf(
            InboxRecordAction("shop", "Shop", InboxRecordDestination("deep_link", "myapp://shop")),
            InboxRecordAction("open", "Open", InboxRecordDestination("open_app", null))
        ),
        sentAt = sentAt,
        collapseId = "tag",
        badge = 2,
        receivedAt = receivedAt,
        attributionJson = """{"tm":"$transmissionId"}"""
    )

    private fun InboxMessageEntity.withEveryTimestamp() = copy(
        expiresAt = EXPIRES_AT,
        updatedAt = UPDATED_AT
    )

    @Test
    fun storedMessageReadsBackWithEveryField() = runBlocking {
        val dao = dao
        val message = message("tm-1", sentAt = 5_000L).withEveryTimestamp()

        assertTrue(dao.insertAndTrim(message, LIMIT))

        assertEquals(message, dao.get("tm-1"))
    }

    @Test
    fun aStateChangeLeavesTheExpiryAndSendTimeAlone() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1", sentAt = SENT_AT).withEveryTimestamp())

        source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 9_000L)

        val stored = source.stored("tm-1")
        assertTrue(stored.isRead)
        assertEquals(9_000L, stored.updatedAt)
        assertEquals(SENT_AT, stored.sentAt)
        assertEquals(EXPIRES_AT, stored.expiresAt)
    }

    @Test
    fun repeatDeliveryKeepsTheFirstCopy() = runBlocking {
        val dao = dao
        val first = message("tm-1")

        assertTrue(dao.insertAndTrim(first, LIMIT))
        assertFalse(dao.insertAndTrim(first.copy(title = "Changed"), LIMIT))

        assertEquals(first, dao.get("tm-1"))
    }

    @Test
    fun concurrentDeliveriesOfOnePushStoreItOnce() = runBlocking {
        val dao = dao

        val results = (1..CONCURRENT_INSERTS).map {
            async(Dispatchers.IO) { dao.insertAndTrim(message("tm-1"), LIMIT) }
        }.awaitAll()

        assertEquals(1, results.count { it })
        assertEquals(listOf("tm-1"), dao.transmissionIdsByCompanyId(COMPANY))
    }

    @Test
    fun retentionKeepsTheNewestBySentAt() = runBlocking {
        val dao = dao
        // Inserted out of order; a push with no send time is stored with sentAt = receivedAt
        dao.insertAndTrim(message("old", sentAt = 100L), 3)
        dao.insertAndTrim(message("new", sentAt = 500L), 3)
        dao.insertAndTrim(message("mid", sentAt = 300L), 3)
        dao.insertAndTrim(message("unsent", receivedAt = 400L), 3)
        dao.insertAndTrim(message("newest", sentAt = 600L), 3)

        assertEquals(listOf("newest", "new", "unsent"), dao.transmissionIdsByCompanyId(COMPANY))
    }

    @Test
    fun retentionBreaksSentAtTiesByID() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a", sentAt = 100L), 2)
        dao.insertAndTrim(message("b", sentAt = 100L), 2)
        dao.insertAndTrim(message("c", sentAt = 100L), 2)

        assertEquals(listOf("c", "b"), dao.transmissionIdsByCompanyId(COMPANY))
    }

    @Test
    fun retentionTrimsEachCompanySeparately() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a1", companyId = "A", sentAt = 100L), 1)
        dao.insertAndTrim(message("b1", companyId = "B", sentAt = 100L), 1)
        dao.insertAndTrim(message("a2", companyId = "A", sentAt = 200L), 1)

        assertEquals(listOf("a2"), dao.transmissionIdsByCompanyId("A"))
        assertEquals(listOf("b1"), dao.transmissionIdsByCompanyId("B"))
    }

    @Test
    fun aDuplicateDoesNotTrim() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a", sentAt = 100L), 5)
        dao.insertAndTrim(message("b", sentAt = 200L), 5)

        dao.insertAndTrim(message("b", sentAt = 200L), 1)

        assertEquals(listOf("b", "a"), dao.transmissionIdsByCompanyId(COMPANY))
    }

    @Test
    fun clearByCompanyIdLeavesOtherCompanies() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a1", companyId = "A"), LIMIT)
        dao.insertAndTrim(message("b1", companyId = "B"), LIMIT)

        dao.deleteByCompanyId("A")

        assertTrue(dao.transmissionIdsByCompanyId("A").isEmpty())
        assertEquals(listOf("b1"), dao.transmissionIdsByCompanyId("B"))
    }

    @Test
    fun clearByCompanyIdReportsHowManyItRemoved() = runBlocking {
        val source = openSource()
        source.seed(
            message("a1", companyId = "A"),
            message("a2", companyId = "A"),
            message("b1", companyId = "B")
        )

        assertEquals(2, source.clearByCompanyId("A"))
        assertEquals(0, source.clearByCompanyId("A"))
    }

    @Test
    fun clearAllRemovesEveryCompany() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a1", companyId = "A"), LIMIT)
        dao.insertAndTrim(message("b1", companyId = "B"), LIMIT)

        dao.deleteAll()

        assertNull(dao.get("a1"))
        assertNull(dao.get("b1"))
    }

    @Test
    fun capturedMessagesSurviveRestart() = runBlocking {
        val first = RoomInboxDataSource(context, databaseName)
        assertEquals(InboxInsertResult.INSERTED, first.insert(message("tm-1"), LIMIT))
        first.close()

        val second = RoomInboxDataSource(context, databaseName)
        assertEquals(InboxInsertResult.DUPLICATE, second.insert(message("tm-1"), LIMIT))
        second.close()

        assertEquals(message("tm-1"), dao.get("tm-1"))
    }

    /**
     * A repository over a fresh data source holding one message for company A and one for company B.
     * Each capture is checked, so a failed write cannot let a clear test pass on an empty store.
     */
    private fun repositoryWithTwoCompanies(dataSource: RoomInboxDataSource): KlaviyoInboxRepository =
        gatedRepository(dataSource).also { repository ->
            runBlocking {
                assertEquals(
                    InboxCaptureResult.CAPTURED,
                    repository.capture(message("a1", companyId = "A"))
                )
                assertEquals(
                    InboxCaptureResult.CAPTURED,
                    repository.capture(message("b1", companyId = "B"))
                )
            }
        }

    @Test
    fun clearByCompanyIdReachesTheStoreThroughTheRepository() = runBlocking {
        val dataSource = RoomInboxDataSource(context, databaseName)
        val repository = repositoryWithTwoCompanies(dataSource)

        repository.clearByCompanyId("A")
        dataSource.close()

        val dao = dao
        assertTrue(dao.transmissionIdsByCompanyId("A").isEmpty())
        assertEquals(listOf("b1"), dao.transmissionIdsByCompanyId("B"))
    }

    @Test
    fun clearAllReachesTheStoreThroughTheRepository() = runBlocking {
        val dataSource = RoomInboxDataSource(context, databaseName)
        val repository = repositoryWithTwoCompanies(dataSource)

        repository.clearAll()
        dataSource.close()

        val dao = dao
        assertTrue(dao.transmissionIdsByCompanyId("A").isEmpty())
        assertTrue(dao.transmissionIdsByCompanyId("B").isEmpty())
    }

    @Test
    fun unreadCountExcludesReadAndArchivedMessages() = runBlocking {
        val source = openSource()
        source.insert(message("unread-1"), LIMIT)
        source.insert(message("unread-2"), LIMIT)
        source.insert(message("read").copy(isRead = true), LIMIT)
        source.insert(message("archived").copy(isArchived = true), LIMIT)
        source.insert(message("both").copy(isRead = true, isArchived = true), LIMIT)

        assertEquals(2, source.countUnread(COMPANY))
    }

    @Test
    fun unreadCountIsScopedToTheCompany() = runBlocking {
        val source = openSource()
        source.insert(message("mine", companyId = "A"), LIMIT)
        source.insert(message("theirs-1", companyId = "B"), LIMIT)
        source.insert(message("theirs-2", companyId = "B"), LIMIT)

        assertEquals(1, source.countUnread("A"))
        assertEquals(2, source.countUnread("B"))
        assertEquals(0, source.countUnread("C"))
    }

    @Test
    fun unreadCountSurvivesRestart() = runBlocking {
        val first = RoomInboxDataSource(context, databaseName)
        first.insert(message("a"), LIMIT)
        first.insert(message("b").copy(isRead = true), LIMIT)
        first.close()

        assertEquals(1, openSource().countUnread(COMPANY))
    }

    @Test
    fun unreadCountReflectsRetention() = runBlocking {
        val source = openSource()
        source.insert(message("old", sentAt = 100L), 1)
        source.insert(message("new", sentAt = 200L).copy(isRead = true), 1)

        assertEquals(0, source.countUnread(COMPANY))
    }

    private suspend fun RoomInboxDataSource.stored(id: String, companyId: String = COMPANY) =
        fetchPage(companyId, InboxRecordQuery(LIMIT, null)).messages.first { it.transmissionId == id }

    @Test
    fun markingAMessageReadStampsUpdatedAt() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))

        assertTrue(source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 7_000L))

        val stored = source.stored("tm-1")
        assertTrue(stored.isRead)
        assertFalse(stored.isArchived)
        assertEquals(7_000L, stored.updatedAt)
    }

    @Test
    fun archivingAMessageStampsUpdatedAt() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))

        assertTrue(source.updateState(COMPANY, "tm-1", InboxStateChange.ARCHIVE, 8_000L))

        val stored = source.stored("tm-1")
        assertTrue(stored.isArchived)
        assertEquals(8_000L, stored.updatedAt)
    }

    @Test
    fun readThenArchiveKeepsTheLatestUpdatedAt() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))

        source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 1_000L)
        source.updateState(COMPANY, "tm-1", InboxStateChange.ARCHIVE, 2_000L)

        val stored = source.stored("tm-1")
        assertEquals(2_000L, stored.updatedAt)
    }

    @Test
    fun aNewMessageHasNoTimestamps() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))

        val stored = source.stored("tm-1")
        assertNull(stored.updatedAt)
        assertNull(stored.expiresAt)
    }

    @Test
    fun readAndArchivedChangeIndependently() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))

        source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 1L)
        source.updateState(COMPANY, "tm-1", InboxStateChange.ARCHIVE, 2L)
        assertTrue(source.stored("tm-1").let { it.isRead && it.isArchived })

        source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_UNREAD, 3L)
        assertTrue(source.stored("tm-1").let { !it.isRead && it.isArchived })
        assertEquals(3L, source.stored("tm-1").updatedAt)

        source.updateState(COMPANY, "tm-1", InboxStateChange.UNARCHIVE, 4L)
        assertTrue(source.stored("tm-1").let { !it.isRead && !it.isArchived })
        assertEquals(4L, source.stored("tm-1").updatedAt)
    }

    @Test
    fun repeatingAChangeKeepsTheFirstTimestampAndReportsTheMessageExists() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))
        source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 1_000L)

        assertTrue(source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 2_000L))

        assertEquals(1_000L, source.stored("tm-1").updatedAt)
    }

    @Test
    fun aChangeThatMatchesTheStoredStateDoesNotStampIt() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))

        assertTrue(source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_UNREAD, 1_000L))

        val stored = source.stored("tm-1")
        assertNull(stored.updatedAt)
    }

    @Test
    fun aMissingMessageIsReportedAndStoresNothing() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))

        assertFalse(source.updateState(COMPANY, "gone", InboxStateChange.MARK_READ, 1L))
        assertFalse(source.stored("tm-1").isRead)
    }

    @Test
    fun anotherCompanysMessageIsNotChanged() = runBlocking {
        val source = openSource()
        source.seed(message("theirs", companyId = "B"))

        assertFalse(source.updateState("A", "theirs", InboxStateChange.MARK_READ, 1L))
        assertFalse(source.stored("theirs", "B").isRead)
    }

    @Test
    fun updatingStateDoesNotCreateTheDatabase() = runBlocking {
        val source = RoomInboxDataSource(context, databaseName)

        assertFalse(source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 1L))

        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    @Test
    fun stateChangesAffectTheUnreadCountAndSurviveRestart() = runBlocking {
        val first = RoomInboxDataSource(context, databaseName)
        first.seed(message("a"), message("b"), message("c"))
        first.updateState(COMPANY, "a", InboxStateChange.MARK_READ, 1L)
        first.updateState(COMPANY, "b", InboxStateChange.ARCHIVE, 2L)
        first.close()

        val second = openSource()
        assertEquals(1, second.countUnread(COMPANY))
        assertTrue(second.stored("a").isRead)
        assertTrue(second.stored("b").isArchived)
        assertEquals(2L, second.stored("b").updatedAt)
    }

    @Test
    fun readsDoNotCreateTheDatabase() = runBlocking {
        val source = RoomInboxDataSource(context, databaseName)

        assertEquals(0, source.countUnread(COMPANY))
        assertTrue(source.fetchPage(COMPANY, InboxRecordQuery(10, null)).messages.isEmpty())

        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    private suspend fun RoomInboxDataSource.bulk(
        change: InboxStateChange,
        read: InboxReadFilter,
        archive: InboxArchiveFilter,
        updatedAt: Long = 9_000L,
        companyId: String = COMPANY
    ) = updateStateWhere(
        companyId,
        change,
        isRead = when (read) {
            InboxReadFilter.ALL -> null
            InboxReadFilter.UNREAD -> false
            InboxReadFilter.READ -> true
        },
        isArchived = when (archive) {
            InboxArchiveFilter.ALL -> null
            InboxArchiveFilter.ACTIVE -> false
            InboxArchiveFilter.ARCHIVED -> true
        },
        updatedAt = updatedAt
    )

    private suspend fun RoomInboxDataSource.updatedIds() = fetchPage(
        COMPANY,
        InboxRecordQuery(LIMIT, null)
    ).messages.filter { it.updatedAt != null }.map { it.transmissionId }.toSet()

    @Test
    fun bulkMarkReadChangesOnlyMatchingMessagesAndCountsThem() = runBlocking {
        val source = openSource()
        source.seedEveryState()

        val changed = source.bulk(
            InboxStateChange.MARK_READ,
            InboxReadFilter.ALL,
            InboxArchiveFilter.ACTIVE
        )

        // read-active already read, and the archived ones are outside the filter
        assertEquals(1, changed)
        assertTrue(source.stored("unread").isRead)
        assertFalse(source.stored("archived-unread").isRead)
        assertEquals(setOf("unread"), source.updatedIds())
        assertEquals(9_000L, source.stored("unread").updatedAt)
    }

    @Test
    fun bulkChangeKeepsTheTimestampOfMessagesAlreadyInTheTargetState() = runBlocking {
        val source = openSource()
        source.seed(
            message("done").copy(isRead = true, updatedAt = 1_234L),
            message("todo")
        )

        assertEquals(
            1,
            source.bulk(InboxStateChange.MARK_READ, InboxReadFilter.ALL, InboxArchiveFilter.ALL)
        )

        assertEquals(1_234L, source.stored("done").updatedAt)
        assertEquals(9_000L, source.stored("todo").updatedAt)
    }

    @Test
    fun bulkChangeLeavesOtherCompaniesAlone() = runBlocking {
        val source = openSource()
        source.seed(message("mine"), message("theirs", companyId = "B"))

        assertEquals(
            1,
            source.bulk(InboxStateChange.MARK_READ, InboxReadFilter.ALL, InboxArchiveFilter.ALL)
        )

        assertTrue(source.stored("mine").isRead)
        assertFalse(source.stored("theirs", "B").isRead)
        assertNull(source.stored("theirs", "B").updatedAt)
    }

    @Test
    fun everyReadAndArchiveFilterMatchesExactlyItsMessages() = runBlocking {
        val expected = mapOf(
            (InboxReadFilter.ALL to InboxArchiveFilter.ALL) to
                setOf("unread", "read", "archived-unread", "archived-read"),
            (InboxReadFilter.ALL to InboxArchiveFilter.ACTIVE) to setOf("unread", "read"),
            (InboxReadFilter.ALL to InboxArchiveFilter.ARCHIVED) to
                setOf("archived-unread", "archived-read"),
            (InboxReadFilter.UNREAD to InboxArchiveFilter.ALL) to
                setOf("unread", "archived-unread"),
            (InboxReadFilter.UNREAD to InboxArchiveFilter.ACTIVE) to setOf("unread"),
            (InboxReadFilter.UNREAD to InboxArchiveFilter.ARCHIVED) to setOf("archived-unread"),
            (InboxReadFilter.READ to InboxArchiveFilter.ALL) to setOf("read", "archived-read"),
            (InboxReadFilter.READ to InboxArchiveFilter.ACTIVE) to setOf("read"),
            (InboxReadFilter.READ to InboxArchiveFilter.ARCHIVED) to setOf("archived-read")
        )
        assertEquals(9, expected.size)

        // MARK_READ can only change the matches that are unread, MARK_UNREAD the ones that are read
        val cases = expected.flatMap { (filter, ids) ->
            listOf(
                Triple(filter, InboxStateChange.MARK_READ, ids.filter { it.endsWith("unread") }),
                Triple(
                    filter,
                    InboxStateChange.MARK_UNREAD,
                    ids.filter { !it.endsWith("unread") }
                )
            )
        }

        cases.forEachIndexed { index, (filter, change, changing) ->
            val name = "$databaseName-$index"
            val source = RoomInboxDataSource(context, name).also { openSources += it }
            source.seedEveryState()

            val changed = source.bulk(change, filter.first, filter.second)

            assertEquals("$filter $change", changing.size, changed)
            assertEquals("$filter $change", changing.toSet(), source.updatedIds())
            source.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun bulkUnarchiveWithTheActiveFilterMatchesNothing() = runBlocking {
        val source = openSource()
        source.seedEveryState()

        assertEquals(
            0,
            source.bulk(InboxStateChange.UNARCHIVE, InboxReadFilter.ALL, InboxArchiveFilter.ACTIVE)
        )
        assertTrue(source.stored("archived-unread").isArchived)
        assertTrue(source.updatedIds().isEmpty())
    }

    @Test
    fun bulkUnarchiveRestoresArchivedMessagesWithTheArchivedFilterOrAllMessages() = runBlocking {
        val archived = openSource()
        archived.seedEveryState()
        assertEquals(
            2,
            archived.bulk(
                InboxStateChange.UNARCHIVE,
                InboxReadFilter.ALL,
                InboxArchiveFilter.ARCHIVED
            )
        )
        assertFalse(archived.stored("archived-unread").isArchived)
        assertFalse(archived.stored("archived-read").isArchived)

        val everything = RoomInboxDataSource(context, "$databaseName-all").also { openSources += it }
        everything.seedEveryState()
        assertEquals(
            2,
            everything.bulk(InboxStateChange.UNARCHIVE, InboxReadFilter.ALL, InboxArchiveFilter.ALL)
        )
        everything.close()
        context.deleteDatabase("$databaseName-all")
        Unit
    }

    @Test
    fun bulkChangeReadsBackThroughTheUnreadCountAndPages() = runBlocking {
        val source = openSource()
        source.seedEveryState()
        assertEquals(1, source.countUnread(COMPANY))

        source.bulk(InboxStateChange.ARCHIVE, InboxReadFilter.UNREAD, InboxArchiveFilter.ACTIVE)

        assertEquals(0, source.countUnread(COMPANY))
        val active = source.fetchPage(COMPANY, InboxRecordQuery(LIMIT, null, isArchived = false))
        assertEquals(listOf("read"), active.messages.map { it.transmissionId })
    }

    @Test
    fun bulkChangeEmitsOnceAndNothingWhenNothingChanged() = runBlocking {
        val source = openSource()
        source.seedEveryState()

        assertEquals(
            1,
            source.emissionsDuring {
                source.bulk(InboxStateChange.MARK_READ, InboxReadFilter.ALL, InboxArchiveFilter.ALL)
            }
        )
        assertEquals(
            0,
            source.emissionsDuring {
                source.bulk(InboxStateChange.MARK_READ, InboxReadFilter.ALL, InboxArchiveFilter.ALL)
            }
        )
    }

    @Test
    fun bulkChangeDoesNotCreateTheDatabase() = runBlocking {
        val source = RoomInboxDataSource(context, databaseName)

        assertEquals(
            0,
            source.bulk(InboxStateChange.MARK_READ, InboxReadFilter.ALL, InboxArchiveFilter.ALL)
        )

        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    private suspend fun RoomInboxDataSource.byIds(
        change: InboxStateChange,
        ids: List<String>,
        updatedAt: Long = 9_000L,
        companyId: String = COMPANY
    ) = updateStateForIds(companyId, change, ids, updatedAt)

    @Test
    fun changeByIdsChangesOnlyTheListedMessagesAndCountsThem() = runBlocking {
        val source = openSource()
        source.seed(message("a"), message("b"), message("c"))

        assertEquals(2, source.byIds(InboxStateChange.MARK_READ, listOf("a", "b")))

        assertTrue(source.stored("a").isRead)
        assertTrue(source.stored("b").isRead)
        assertFalse(source.stored("c").isRead)
        assertEquals(setOf("a", "b"), source.updatedIds())
        assertEquals(9_000L, source.stored("a").updatedAt)
        assertEquals(9_000L, source.stored("b").updatedAt)
    }

    @Test
    fun changeByIdsAppliesEveryStateChange() = runBlocking {
        val source = openSource()
        source.seed(message("a"), message("b").copy(isRead = true, isArchived = true))

        assertEquals(1, source.byIds(InboxStateChange.ARCHIVE, listOf("a", "b")))
        assertTrue(source.stored("a").isArchived)
        assertEquals(2, source.byIds(InboxStateChange.UNARCHIVE, listOf("a", "b")))
        assertFalse(source.stored("a").isArchived)
        assertFalse(source.stored("b").isArchived)
        assertEquals(1, source.byIds(InboxStateChange.MARK_READ, listOf("a", "b")))
        assertEquals(2, source.byIds(InboxStateChange.MARK_UNREAD, listOf("a", "b")))
        assertFalse(source.stored("b").isRead)
    }

    @Test
    fun changeByIdsKeepsTheTimestampOfMessagesAlreadyInTheTargetState() = runBlocking {
        val source = openSource()
        source.seed(message("done").copy(isRead = true, updatedAt = 1_234L), message("todo"))

        assertEquals(1, source.byIds(InboxStateChange.MARK_READ, listOf("done", "todo")))

        assertEquals(1_234L, source.stored("done").updatedAt)
        assertEquals(9_000L, source.stored("todo").updatedAt)
        assertEquals(0, source.byIds(InboxStateChange.MARK_READ, listOf("done", "todo"), 12_000L))
        assertEquals(9_000L, source.stored("todo").updatedAt)
    }

    @Test
    fun changeByIdsIgnoresUnknownIdsOtherCompaniesAndCountsDuplicatesOnce() = runBlocking {
        val source = openSource()
        source.seed(message("mine"), message("theirs", companyId = "B"))

        val changed = source.byIds(
            InboxStateChange.MARK_READ,
            listOf("mine", "mine", "theirs", "unknown")
        )

        assertEquals(1, changed)
        assertTrue(source.stored("mine").isRead)
        assertFalse(source.stored("theirs", "B").isRead)
        assertNull(source.stored("theirs", "B").updatedAt)
    }

    @Test
    fun changeByAnEmptyListChangesNothingAndDoesNotCreateTheDatabase() = runBlocking {
        val source = RoomInboxDataSource(context, databaseName)

        assertEquals(0, source.byIds(InboxStateChange.MARK_READ, emptyList()))

        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    @Test
    fun changeByIdsOnAMissingDatabaseDoesNotCreateIt() = runBlocking {
        val source = RoomInboxDataSource(context, databaseName)

        assertEquals(0, source.byIds(InboxStateChange.MARK_READ, listOf("a")))

        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    @Test
    fun changeByIdsEmitsOnceAndNothingWhenNothingChanged() = runBlocking {
        val source = openSource()
        source.seed(message("a"), message("b"))

        assertEquals(
            1,
            source.emissionsDuring { source.byIds(InboxStateChange.MARK_READ, listOf("a", "b")) }
        )
        assertEquals(
            0,
            source.emissionsDuring { source.byIds(InboxStateChange.MARK_READ, listOf("a", "b")) }
        )
        assertEquals(
            0,
            source.emissionsDuring { source.byIds(InboxStateChange.MARK_READ, listOf("nope")) }
        )
        assertEquals(
            0,
            source.emissionsDuring { source.byIds(InboxStateChange.MARK_READ, emptyList()) }
        )
    }

    @Test
    fun changeByMoreIdsThanOneStatementAllowsChangesAllOfThemWithOneEmission() = runBlocking {
        val source = openSource()
        val count = 2_500
        val ids = (1..count).map { "tm-$it" }
        // Room trims to the retention limit on insert, so seed with a limit above the count
        ids.forEach { source.insert(message(it), count) }

        // Duplicates and unknown IDs spread across the chunks must not skew the sum
        val request = ids + ids.take(1_000) + (1..1_000).map { "unknown-$it" }
        var changed = 0
        val emissions = source.emissionsDuring {
            changed = source.byIds(InboxStateChange.MARK_READ, request)
        }

        assertEquals(count, changed)
        assertEquals(1, emissions)
        assertEquals(0, source.countUnread(COMPANY))
        assertEquals(
            count,
            source.fetchPage(COMPANY, InboxRecordQuery(count, null)).messages.count { it.updatedAt == 9_000L }
        )
    }

    @Test
    fun changeByExactlyTheChunkBoundariesCountsEveryMessage() = runBlocking {
        val max = InboxMessageDao.MAX_IDS_PER_STATEMENT
        listOf(max - 1, max, max + 1, 999, 1_000).forEach { size ->
            val name = "inbox-boundary-${UUID.randomUUID()}.db"
            val source = RoomInboxDataSource(context, name)
            val ids = (1..size).map { "tm-$it" }
            ids.forEach { source.insert(message(it), size) }

            assertEquals(
                "size $size",
                size,
                source.updateStateForIds(COMPANY, InboxStateChange.ARCHIVE, ids, 9_000L)
            )

            source.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun RoomInboxDataSource.seed(vararg messages: InboxMessageEntity) =
        messages.forEach { insert(it, LIMIT) }

    @Test
    fun storingAPushEmitsOnceAndADuplicateDoesNot() = runBlocking {
        val source = openSource()

        assertEquals(1, source.emissionsDuring { source.insert(message("tm-1"), LIMIT) })
        assertEquals(0, source.emissionsDuring { source.insert(message("tm-1"), LIMIT) })
    }

    @Test
    fun aStoredPushThatTrimsEmitsOnce() = runBlocking {
        val source = openSource()
        source.insert(message("old", receivedAt = 1L), 1)

        assertEquals(
            1,
            source.emissionsDuring { source.insert(message("new", receivedAt = 2L), 1) }
        )
        assertEquals(listOf("new"), source.ids(10))
    }

    @Test
    fun aStateChangeEmitsOnlyWhenItAltersAMessage() = runBlocking {
        val source = openSource()
        source.seed(message("tm-1"))

        assertEquals(
            1,
            source.emissionsDuring {
                source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 1L)
            }
        )
        assertEquals(
            0,
            source.emissionsDuring {
                source.updateState(COMPANY, "tm-1", InboxStateChange.MARK_READ, 2L)
            }
        )
        assertEquals(
            0,
            source.emissionsDuring {
                source.updateState(COMPANY, "gone", InboxStateChange.MARK_READ, 3L)
            }
        )
    }

    @Test
    fun clearingEmitsOnlyWhenItRemovedMessages() = runBlocking {
        val source = openSource()
        source.seed(message("a"), message("b", companyId = "other"))

        assertEquals(0, source.emissionsDuring { source.clearByCompanyId("nobody") })
        assertEquals(1, source.emissionsDuring { source.clearByCompanyId(COMPANY) })
        assertEquals(1, source.emissionsDuring { source.clearAll() })
        assertEquals(0, source.emissionsDuring { source.clearAll() })
    }

    @Test
    fun collectingChangesDoesNotCreateTheDatabase() = runBlocking {
        val source = RoomInboxDataSource(context, databaseName)

        source.emissionsDuring { }

        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    @Test
    fun aSlowCollectorMergesChangesAndNeverBlocksTheWriter() = runBlocking {
        val source = openSource()
        val release = CompletableDeferred<Unit>()
        var received = 0
        val collector = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            source.changes.collect {
                received++
                release.await()
            }
        }

        source.seed(message("a"), message("b"), message("c"))
        release.complete(Unit)
        delay(EMISSIONS_SETTLE_MS)
        collector.cancel()

        // The first is taken, the next two merge into one
        assertEquals(2, received)
        assertEquals(3, source.ids(10).size)
    }

    private suspend fun RoomInboxDataSource.ids(
        pageSize: Int,
        after: InboxRecordPosition? = null,
        companyId: String = COMPANY,
        isRead: Boolean? = null,
        isArchived: Boolean? = null
    ) = fetchPage(companyId, InboxRecordQuery(pageSize, after, isRead, isArchived))
        .messages.map { it.transmissionId }

    @Test
    fun pagesRunNewestFirstBySentAt() = runBlocking {
        val source = openSource()
        source.seed(
            message("old", sentAt = 100L),
            message("unsent", receivedAt = 400L),
            message("new", sentAt = 500L),
            message("mid", sentAt = 300L)
        )

        assertEquals(listOf("new", "unsent", "mid", "old"), source.ids(10))
    }

    @Test
    fun pagingWalksEveryMessageOnceAndFlagsTheLastPage() = runBlocking {
        val source = openSource()
        source.seed(*(1..5).map { message("m$it", sentAt = it * 100L) }.toTypedArray())

        val first = source.fetchPage(COMPANY, InboxRecordQuery(2, null))
        val second = source.fetchPage(
            COMPANY,
            InboxRecordQuery(2, first.messages.last().position())
        )
        val third = source.fetchPage(
            COMPANY,
            InboxRecordQuery(2, second.messages.last().position())
        )

        assertEquals(listOf("m5", "m4"), first.messages.map { it.transmissionId })
        assertTrue(first.hasMore)
        assertEquals(listOf("m3", "m2"), second.messages.map { it.transmissionId })
        assertTrue(second.hasMore)
        assertEquals(listOf("m1"), third.messages.map { it.transmissionId })
        assertFalse(third.hasMore)
    }

    @Test
    fun aPageThatEndsExactlyAtTheLastMessageHasNoMore() = runBlocking {
        val source = openSource()
        source.seed(message("a", sentAt = 100L), message("b", sentAt = 200L))

        val page = source.fetchPage(COMPANY, InboxRecordQuery(2, null))

        assertEquals(2, page.messages.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun messagesSharingASentAtAreOrderedAndPagedByID() = runBlocking {
        val source = openSource()
        source.seed(
            message("a", sentAt = 100L),
            message("b", sentAt = 100L),
            message("c", sentAt = 100L)
        )

        val first = source.fetchPage(COMPANY, InboxRecordQuery(1, null))
        val second = source.fetchPage(
            COMPANY,
            InboxRecordQuery(1, first.messages.last().position())
        )

        assertEquals(listOf("c"), first.messages.map { it.transmissionId })
        assertEquals(listOf("b"), second.messages.map { it.transmissionId })
        assertEquals(listOf("a"), source.ids(5, second.messages.last().position()))
    }

    @Test
    fun aMessageCapturedBetweenPagesDoesNotShiftTheNextPage() = runBlocking {
        val source = openSource()
        source.seed(
            message("a", sentAt = 100L),
            message("b", sentAt = 200L),
            message("c", sentAt = 300L)
        )

        val first = source.fetchPage(COMPANY, InboxRecordQuery(1, null))
        source.seed(message("newer", sentAt = 900L))

        assertEquals(listOf("c"), first.messages.map { it.transmissionId })
        assertEquals(listOf("b", "a"), source.ids(5, first.messages.last().position()))
    }

    @Test
    fun aMessageRemovedBetweenPagesDoesNotSkipTheNextOne() = runBlocking {
        val source = openSource()
        source.seed(
            message("a", sentAt = 100L),
            message("b", sentAt = 200L),
            message("c", sentAt = 300L)
        )

        val first = source.fetchPage(COMPANY, InboxRecordQuery(1, null))
        source.clearByCompanyId(COMPANY)
        source.seed(message("a", sentAt = 100L), message("b", sentAt = 200L))

        assertEquals(listOf("b", "a"), source.ids(5, first.messages.last().position()))
    }

    @Test
    fun pagesAreScopedToTheCompany() = runBlocking {
        val source = openSource()
        source.seed(
            message("mine", companyId = "A", sentAt = 100L),
            message("theirs", companyId = "B", sentAt = 200L)
        )

        assertEquals(listOf("mine"), source.ids(10, companyId = "A"))
        assertEquals(listOf("theirs"), source.ids(10, companyId = "B"))
        assertTrue(source.ids(10, companyId = "C").isEmpty())
    }

    @Test
    fun pagesIncludeReadAndArchivedMessages() = runBlocking {
        val source = openSource()
        source.seed(
            message("unread", sentAt = 300L),
            message("read", sentAt = 200L).copy(isRead = true),
            message("archived", sentAt = 100L).copy(isArchived = true)
        )

        assertEquals(listOf("unread", "read", "archived"), source.ids(10))
    }

    private suspend fun RoomInboxDataSource.seedEveryState() = seed(
        message("unread", sentAt = 400L),
        message("read", sentAt = 300L).copy(isRead = true),
        message("archived-unread", sentAt = 200L).copy(isArchived = true),
        message("archived-read", sentAt = 100L).copy(isRead = true, isArchived = true)
    )

    @Test
    fun pagesFilterByReadState() = runBlocking {
        val source = openSource()
        source.seedEveryState()

        assertEquals(listOf("unread", "archived-unread"), source.ids(10, isRead = false))
        assertEquals(listOf("read", "archived-read"), source.ids(10, isRead = true))
    }

    @Test
    fun pagesFilterByArchivedState() = runBlocking {
        val source = openSource()
        source.seedEveryState()

        assertEquals(listOf("unread", "read"), source.ids(10, isArchived = false))
        assertEquals(listOf("archived-unread", "archived-read"), source.ids(10, isArchived = true))
    }

    @Test
    fun pagesCombineTheReadAndArchivedFilters() = runBlocking {
        val source = openSource()
        source.seedEveryState()

        assertEquals(listOf("unread"), source.ids(10, isRead = false, isArchived = false))
        assertEquals(listOf("archived-read"), source.ids(10, isRead = true, isArchived = true))
    }

    @Test
    fun aFilteredPageWalksOnlyMatchingMessagesAcrossPages() = runBlocking {
        val source = openSource()
        source.seed(
            *(1..6).map {
                message("m$it", sentAt = it * 100L).copy(isArchived = it % 2 == 0)
            }.toTypedArray()
        )

        val first = source.fetchPage(COMPANY, InboxRecordQuery(2, null, isArchived = false))
        val second = source.fetchPage(
            COMPANY,
            InboxRecordQuery(2, first.messages.last().position(), isArchived = false)
        )

        assertEquals(listOf("m5", "m3"), first.messages.map { it.transmissionId })
        assertTrue(first.hasMore)
        assertEquals(listOf("m1"), second.messages.map { it.transmissionId })
        assertFalse(second.hasMore)
    }

    @Test
    fun aFilteredPageStaysStableWhenAMessageLeavesTheFilterBetweenPages() = runBlocking {
        val source = openSource()
        source.seed(*(1..4).map { message("m$it", sentAt = it * 100L) }.toTypedArray())
        val first = source.fetchPage(COMPANY, InboxRecordQuery(2, null, isRead = false))

        // The message the first page ended on is read, so it leaves the filter
        source.updateState(COMPANY, "m3", InboxStateChange.MARK_READ, 1L)
        val second = source.fetchPage(
            COMPANY,
            InboxRecordQuery(2, first.messages.last().position(), isRead = false)
        )

        assertEquals(listOf("m4", "m3"), first.messages.map { it.transmissionId })
        assertEquals(listOf("m2", "m1"), second.messages.map { it.transmissionId })
    }

    @Test
    fun filteredPagesSurviveRestart() = runBlocking {
        val first = RoomInboxDataSource(context, databaseName)
        first.seedEveryState()
        first.close()

        assertEquals(listOf("unread"), openSource().ids(10, isRead = false, isArchived = false))
    }

    @Test
    fun pagesSurviveRestart() = runBlocking {
        val first = RoomInboxDataSource(context, databaseName)
        first.seed(message("a", sentAt = 100L), message("b", sentAt = 200L))
        first.close()

        assertEquals(listOf("b", "a"), openSource().ids(10))
    }

    @Test
    fun pagesReflectRetention() = runBlocking {
        val source = openSource()
        source.insert(message("old", sentAt = 100L), 2)
        source.insert(message("mid", sentAt = 200L), 2)
        source.insert(message("new", sentAt = 300L), 2)

        assertEquals(listOf("new", "mid"), source.ids(10))
    }

    @Test
    fun aMessageReadsBackThroughAPageWithEveryField() = runBlocking {
        val source = openSource()
        val stored = message("tm-1", sentAt = 5_000L).withEveryTimestamp()
        source.seed(stored)

        assertEquals(
            listOf(stored),
            source.fetchPage(COMPANY, InboxRecordQuery(10, null)).messages
        )
    }

    private fun InboxMessageEntity.position() = InboxRecordPosition(sentAt, transmissionId)

    @Test
    fun exportedVersionOneSchemaMatchesTheEntity() {
        migrationHelper.createDatabase(databaseName, 1).close()

        migrationHelper.runMigrationsAndValidate(databaseName, 1, true).close()
    }

    private companion object {
        const val COMPANY = "company"
        const val LIMIT = 100
        const val CONCURRENT_INSERTS = 20
        const val EXPIRES_AT = 90_000L
        const val UPDATED_AT = 5_000L
        const val SENT_AT = 4_000L
    }
}
