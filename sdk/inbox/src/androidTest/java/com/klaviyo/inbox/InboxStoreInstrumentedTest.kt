package com.klaviyo.inbox

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
        sentAt: Long? = null,
        receivedAt: Long = 1_000L
    ) = InboxMessageEntity(
        transmissionId = transmissionId,
        companyId = companyId,
        title = "Title $transmissionId",
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

    @Test
    fun storedMessageReadsBackWithEveryField() = runBlocking {
        val dao = dao
        val message = message("tm-1", sentAt = 5_000L)

        assertTrue(dao.insertAndTrim(message, LIMIT))

        assertEquals(message, dao.get("tm-1"))
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
    fun retentionKeepsTheNewestBySortTime() = runBlocking {
        val dao = dao
        // Inserted out of order; sort time is sentAt, falling back to receivedAt
        dao.insertAndTrim(message("old", sentAt = 100L), 3)
        dao.insertAndTrim(message("new", sentAt = 500L), 3)
        dao.insertAndTrim(message("mid", sentAt = 300L), 3)
        dao.insertAndTrim(message("unsent", sentAt = null, receivedAt = 400L), 3)
        dao.insertAndTrim(message("newest", sentAt = 600L), 3)

        assertEquals(listOf("newest", "new", "unsent"), dao.transmissionIdsByCompanyId(COMPANY))
    }

    @Test
    fun retentionBreaksSortTimeTiesByID() = runBlocking {
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

    @Test
    fun dataSourceClearsThroughTheRepository() = runBlocking {
        val dataSource = RoomInboxDataSource(context, databaseName)
        val repository = KlaviyoInboxRepository(dataSource)
        repository.capture(message("a1", companyId = "A"))
        repository.capture(message("b1", companyId = "B"))

        repository.clearByCompanyId("A")
        dataSource.close()

        val dao = dao
        assertTrue(dao.transmissionIdsByCompanyId("A").isEmpty())
        assertEquals(listOf("b1"), dao.transmissionIdsByCompanyId("B"))
    }

    private fun query(
        pageSize: Int = 20,
        after: InboxRecordPosition? = null,
        read: InboxReadFilter = InboxReadFilter.ALL,
        archive: InboxArchiveFilter = InboxArchiveFilter.ALL,
        order: InboxSortOrder = InboxSortOrder.NEWEST_FIRST
    ) = InboxRecordQuery(pageSize, after, read, archive, order)

    private fun InboxRecordPage.ids() = messages.map { it.transmissionId }

    private suspend fun RoomInboxDataSource.seed(vararg ids: Pair<String, Long>) =
        ids.forEach { (id, sentAt) -> insert(message(id, sentAt = sentAt), LIMIT) }

    @Test
    fun pagesWalkEveryMessageOnceInBothOrders() = runBlocking {
        val source = openSource()
        source.seed("a" to 100L, "b" to 200L, "c" to 300L, "d" to 400L, "e" to 500L)

        for (order in InboxSortOrder.values()) {
            val seen = mutableListOf<String>()
            var after: InboxRecordPosition? = null
            do {
                val page = source.fetchPage(
                    COMPANY,
                    query(pageSize = 2, after = after, order = order)
                )
                seen += page.ids()
                after = page.messages.lastOrNull()
                    ?.let { InboxRecordPosition(it.sortTime, it.transmissionId) }
            } while (page.hasMore)

            val expected = listOf("e", "d", "c", "b", "a")
            assertEquals(
                order.name,
                if (order == InboxSortOrder.NEWEST_FIRST) expected else expected.reversed(),
                seen
            )
        }
    }

    @Test
    fun pagesBreakSortTimeTiesByID() = runBlocking {
        val source = openSource()
        source.seed("a" to 100L, "b" to 100L, "c" to 100L, "d" to 100L)

        val first = source.fetchPage(COMPANY, query(pageSize = 2))
        assertEquals(listOf("d", "c"), first.ids())
        assertTrue(first.hasMore)

        val last = first.messages.last()
        val second = source.fetchPage(
            COMPANY,
            query(pageSize = 2, after = InboxRecordPosition(last.sortTime, last.transmissionId))
        )
        assertEquals(listOf("b", "a"), second.ids())
        assertFalse(second.hasMore)
    }

    @Test
    fun aPageBoundaryHoldsWhenANewerMessageArrivesBetweenPages() = runBlocking {
        val source = openSource()
        source.seed("a" to 100L, "b" to 200L, "c" to 300L)

        val first = source.fetchPage(COMPANY, query(pageSize = 2))
        source.seed("new" to 900L)
        val last = first.messages.last()
        val second = source.fetchPage(
            COMPANY,
            query(pageSize = 2, after = InboxRecordPosition(last.sortTime, last.transmissionId))
        )

        assertEquals(listOf("c", "b"), first.ids())
        assertEquals(listOf("a"), second.ids())
    }

    @Test
    fun unsentMessagesSortByTheTimeTheyWereReceived() = runBlocking {
        val source = openSource()
        source.seed("sent" to 300L)
        source.insert(message("unsent", sentAt = null, receivedAt = 500L), LIMIT)

        assertEquals(listOf("unsent", "sent"), source.fetchPage(COMPANY, query()).ids())
    }

    @Test
    fun filtersSelectOnReadAndArchivedIndependently() = runBlocking {
        val source = openSource()
        source.seed("plain" to 100L, "read" to 200L, "archived" to 300L, "both" to 400L)
        source.updateState(COMPANY, "read", InboxRecordStateChange.MARK_READ)
        source.updateState(COMPANY, "archived", InboxRecordStateChange.ARCHIVE)
        source.updateState(COMPANY, "both", InboxRecordStateChange.MARK_READ)
        source.updateState(COMPANY, "both", InboxRecordStateChange.ARCHIVE)

        fun ids(read: InboxReadFilter, archive: InboxArchiveFilter) = runBlocking {
            source.fetchPage(COMPANY, query(read = read, archive = archive)).ids()
        }

        assertEquals(
            listOf("both", "archived", "read", "plain"),
            ids(InboxReadFilter.ALL, InboxArchiveFilter.ALL)
        )
        assertEquals(
            listOf("read", "plain"),
            ids(InboxReadFilter.ALL, InboxArchiveFilter.UNARCHIVED)
        )
        assertEquals(
            listOf("both", "archived"),
            ids(InboxReadFilter.ALL, InboxArchiveFilter.ARCHIVED)
        )
        assertEquals(listOf("both", "read"), ids(InboxReadFilter.READ, InboxArchiveFilter.ALL))
        assertEquals(
            listOf("archived", "plain"),
            ids(InboxReadFilter.UNREAD, InboxArchiveFilter.ALL)
        )
        assertEquals(listOf("plain"), ids(InboxReadFilter.UNREAD, InboxArchiveFilter.UNARCHIVED))
        assertEquals(listOf("both"), ids(InboxReadFilter.READ, InboxArchiveFilter.ARCHIVED))
    }

    @Test
    fun pagesAreScopedToTheCompany() = runBlocking {
        val source = openSource()
        source.insert(message("mine", companyId = "A", sentAt = 100L), LIMIT)
        source.insert(message("theirs", companyId = "B", sentAt = 200L), LIMIT)

        assertEquals(listOf("mine"), source.fetchPage("A", query()).ids())
        assertTrue(source.fetchPage("C", query()).messages.isEmpty())
    }

    @Test
    fun unreadCountExcludesReadAndArchivedMessages() = runBlocking {
        val source = openSource()
        source.seed("a" to 100L, "b" to 200L, "c" to 300L, "d" to 400L)
        source.updateState(COMPANY, "b", InboxRecordStateChange.MARK_READ)
        source.updateState(COMPANY, "c", InboxRecordStateChange.ARCHIVE)

        assertEquals(2, source.countUnread(COMPANY))
        assertEquals(0, source.countUnread("other"))
    }

    @Test
    fun stateChangesAreIdempotentAndIndependent() = runBlocking {
        val source = openSource()
        source.seed("a" to 100L)

        assertTrue(source.updateState(COMPANY, "a", InboxRecordStateChange.MARK_READ))
        assertFalse(source.updateState(COMPANY, "a", InboxRecordStateChange.MARK_READ))
        assertTrue(source.updateState(COMPANY, "a", InboxRecordStateChange.ARCHIVE))
        assertFalse(source.updateState(COMPANY, "a", InboxRecordStateChange.ARCHIVE))

        val stored = source.fetchPage(COMPANY, query()).messages.single()
        assertTrue(stored.isRead)
        assertTrue(stored.isArchived)

        assertTrue(source.updateState(COMPANY, "a", InboxRecordStateChange.MARK_UNREAD))
        assertTrue(source.updateState(COMPANY, "a", InboxRecordStateChange.UNARCHIVE))
        val reset = source.fetchPage(COMPANY, query()).messages.single()
        assertFalse(reset.isRead)
        assertFalse(reset.isArchived)
    }

    @Test
    fun stateChangesForUnknownMessagesOrOtherCompaniesDoNothing() = runBlocking {
        val source = openSource()
        source.seed("a" to 100L)

        assertFalse(source.updateState(COMPANY, "missing", InboxRecordStateChange.MARK_READ))
        assertFalse(source.updateState("other", "a", InboxRecordStateChange.MARK_READ))
        assertFalse(source.fetchPage(COMPANY, query()).messages.single().isRead)
    }

    @Test
    fun readAndArchivedStateSurviveRestart() = runBlocking {
        val first = RoomInboxDataSource(context, databaseName)
        first.seed("a" to 100L, "b" to 200L)
        first.updateState(COMPANY, "a", InboxRecordStateChange.MARK_READ)
        first.updateState(COMPANY, "b", InboxRecordStateChange.ARCHIVE)
        first.close()

        val second = openSource()
        val all = second.fetchPage(COMPANY, query()).messages.associateBy { it.transmissionId }
        assertTrue(all.getValue("a").isRead)
        assertTrue(all.getValue("b").isArchived)
        assertFalse(second.updateState(COMPANY, "a", InboxRecordStateChange.MARK_READ))
    }

    @Test
    fun readsDoNotCreateTheDatabase() = runBlocking {
        val source = RoomInboxDataSource(context, databaseName)

        assertTrue(source.fetchPage(COMPANY, query()).messages.isEmpty())
        assertEquals(0, source.countUnread(COMPANY))
        assertFalse(source.updateState(COMPANY, "a", InboxRecordStateChange.MARK_READ))
        source.clearAll()

        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    @Test
    fun changesEmitForWritesThatChangeSomethingOnly() = runBlocking {
        val source = openSource()
        var emissions = 0
        val job = source.changes.onEach { emissions++ }.launchIn(this)
        yield()

        source.insert(message("a", sentAt = 100L), LIMIT)
        yield()
        assertEquals("insert", 1, emissions)

        source.insert(message("a", sentAt = 100L), LIMIT)
        yield()
        assertEquals("duplicate", 1, emissions)

        source.updateState(COMPANY, "a", InboxRecordStateChange.MARK_READ)
        yield()
        assertEquals("state change", 2, emissions)

        source.updateState(COMPANY, "a", InboxRecordStateChange.MARK_READ)
        source.updateState(COMPANY, "missing", InboxRecordStateChange.ARCHIVE)
        yield()
        assertEquals("no-op state change", 2, emissions)

        source.clearByCompanyId("empty")
        yield()
        assertEquals("clear that removed nothing", 2, emissions)

        source.clearByCompanyId(COMPANY)
        yield()
        assertEquals("clear", 3, emissions)

        job.cancel()
    }

    @Test
    fun aRetentionTrimEmitsOnceWithTheInsert() = runBlocking {
        val source = openSource()
        source.insert(message("old", sentAt = 100L), 1)
        var emissions = 0
        val job = source.changes.onEach { emissions++ }.launchIn(this)
        yield()

        source.insert(message("new", sentAt = 200L), 1)
        yield()

        assertEquals(1, emissions)
        assertEquals(listOf("new"), source.fetchPage(COMPANY, query()).ids())
        job.cancel()
    }

    @Test
    fun pageQueriesUseTheSortIndex() {
        val db = openDatabase()
        val plan = mutableListOf<String>()
        listOf("DESC", "ASC").forEach { direction ->
            db.openHelper.readableDatabase.query(
                "EXPLAIN QUERY PLAN SELECT * FROM ${InboxMessageEntity.TABLE_NAME} " +
                    "WHERE companyId = 'c' AND isRead = 0 " +
                    "ORDER BY sortTime $direction, transmissionId $direction LIMIT 21"
            ).use { cursor ->
                while (cursor.moveToNext()) plan += cursor.getString(
                    cursor.getColumnIndexOrThrow("detail")
                )
            }
        }

        assertTrue(
            plan.joinToString("\n"),
            plan.all {
                it.contains(
                    "USING INDEX index_inbox_message_companyId_sortTime_transmissionId"
                ) || it.contains("USING COVERING INDEX")
            }
        )
        assertFalse(plan.joinToString("\n"), plan.any { it.contains("USE TEMP B-TREE") })
    }

    @Test
    fun exportedVersionOneSchemaMatchesTheEntity() {
        migrationHelper.createDatabase(databaseName, 1).close()

        migrationHelper.runMigrationsAndValidate(databaseName, 1, true).close()
    }

    private companion object {
        const val COMPANY = "company"
        const val LIMIT = 100
        const val CONCURRENT_INSERTS = 20
    }
}
