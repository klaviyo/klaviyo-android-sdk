package com.klaviyo.inbox

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    /**
     * A repository over a fresh data source holding one message for company A and one for company B.
     * Each capture is checked, so a failed write cannot let a clear test pass on an empty store.
     */
    private fun repositoryWithTwoCompanies(dataSource: RoomInboxDataSource): KlaviyoInboxRepository =
        KlaviyoInboxRepository(dataSource).also { repository ->
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

    @Test
    fun readsDoNotCreateTheDatabase() = runBlocking {
        val source = RoomInboxDataSource(context, databaseName)

        assertEquals(0, source.countUnread(COMPANY))
        assertTrue(source.fetchPage(COMPANY, InboxRecordQuery(10, null)).messages.isEmpty())

        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    private suspend fun RoomInboxDataSource.seed(vararg messages: InboxMessageEntity) =
        messages.forEach { insert(it, LIMIT) }

    private suspend fun RoomInboxDataSource.ids(
        pageSize: Int,
        after: InboxRecordPosition? = null,
        companyId: String = COMPANY
    ) = fetchPage(companyId, InboxRecordQuery(pageSize, after)).messages.map { it.transmissionId }

    @Test
    fun pagesRunNewestFirstBySortTime() = runBlocking {
        val source = openSource()
        source.seed(
            message("old", sentAt = 100L),
            message("unsent", sentAt = null, receivedAt = 400L),
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
    fun messagesSharingASortTimeAreOrderedAndPagedByID() = runBlocking {
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
        val stored = message("tm-1", sentAt = 5_000L)
        source.seed(stored)

        assertEquals(
            listOf(stored),
            source.fetchPage(COMPANY, InboxRecordQuery(10, null)).messages
        )
    }

    private fun InboxMessageEntity.position() = InboxRecordPosition(sortTime, transmissionId)

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
