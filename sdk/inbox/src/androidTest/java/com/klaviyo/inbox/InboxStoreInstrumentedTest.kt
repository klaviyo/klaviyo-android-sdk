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
        openDatabases.forEach { it.close() }
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(): InboxDatabase = Room.databaseBuilder(
        context,
        InboxDatabase::class.java,
        databaseName
    ).build().also { openDatabases += it }

    private val dao get() = openDatabase().messageDao()

    private fun message(
        id: String,
        companyId: String = COMPANY,
        sentAt: Long? = null,
        receivedAt: Long = 1_000L
    ) = InboxMessageEntity(
        id = id,
        companyId = companyId,
        title = "Title $id",
        body = "Body $id",
        defaultDestination = InboxRecordDestination("deep_link", "myapp://sale"),
        mediaUrl = "https://cdn.example.com/$id.png",
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
        attributionJson = """{"tm":"$id"}"""
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
        assertEquals(listOf("tm-1"), dao.idsByCompanyId(COMPANY))
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

        assertEquals(listOf("newest", "new", "unsent"), dao.idsByCompanyId(COMPANY))
    }

    @Test
    fun retentionBreaksSortTimeTiesByID() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a", sentAt = 100L), 2)
        dao.insertAndTrim(message("b", sentAt = 100L), 2)
        dao.insertAndTrim(message("c", sentAt = 100L), 2)

        assertEquals(listOf("c", "b"), dao.idsByCompanyId(COMPANY))
    }

    @Test
    fun retentionTrimsEachCompanySeparately() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a1", companyId = "A", sentAt = 100L), 1)
        dao.insertAndTrim(message("b1", companyId = "B", sentAt = 100L), 1)
        dao.insertAndTrim(message("a2", companyId = "A", sentAt = 200L), 1)

        assertEquals(listOf("a2"), dao.idsByCompanyId("A"))
        assertEquals(listOf("b1"), dao.idsByCompanyId("B"))
    }

    @Test
    fun aDuplicateDoesNotTrim() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a", sentAt = 100L), 5)
        dao.insertAndTrim(message("b", sentAt = 200L), 5)

        dao.insertAndTrim(message("b", sentAt = 200L), 1)

        assertEquals(listOf("b", "a"), dao.idsByCompanyId(COMPANY))
    }

    @Test
    fun clearByCompanyIdLeavesOtherCompanies() = runBlocking {
        val dao = dao
        dao.insertAndTrim(message("a1", companyId = "A"), LIMIT)
        dao.insertAndTrim(message("b1", companyId = "B"), LIMIT)

        dao.deleteByCompanyId("A")

        assertTrue(dao.idsByCompanyId("A").isEmpty())
        assertEquals(listOf("b1"), dao.idsByCompanyId("B"))
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
        assertTrue(dao.idsByCompanyId("A").isEmpty())
        assertEquals(listOf("b1"), dao.idsByCompanyId("B"))
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
