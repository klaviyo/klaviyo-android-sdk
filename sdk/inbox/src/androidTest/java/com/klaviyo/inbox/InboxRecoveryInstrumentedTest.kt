package com.klaviyo.inbox

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A store that cannot be opened or read must not stop capture. SQLite and Room delete a database
 * they find corrupt and create a new one, so these tests pin that the inbox keeps accepting
 * messages afterwards.
 */
class InboxRecoveryInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "inbox-recovery-${UUID.randomUUID()}.db"
    private val databaseFile: File get() = context.getDatabasePath(databaseName)
    private val openSources = mutableListOf<RoomInboxDataSource>()

    @Before
    fun setup() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanup() {
        openSources.forEach { it.close() }
        context.deleteDatabase(databaseName)
    }

    private fun dataSource() = RoomInboxDataSource(context, databaseName).also { openSources += it }

    private fun message(id: String) = InboxMessageEntity(
        transmissionId = id,
        companyId = COMPANY,
        title = "Title $id",
        body = "Body $id",
        defaultDestination = InboxRecordDestination("open_app", null),
        mediaUrl = null,
        mediaType = null,
        customData = emptyMap(),
        actions = emptyList(),
        sentAt = null,
        collapseId = null,
        badge = null,
        receivedAt = 1_000L,
        attributionJson = null
    )

    /**
     * Stores [count] messages, then closes the database so its file can be damaged
     */
    private fun seed(count: Int) = runBlocking {
        val source = dataSource()
        repeat(count) { source.insert(message("seed-$it"), LIMIT) }
        source.close()
        openSources.remove(source)
    }

    /**
     * Rewrites the closed database file with the bytes [change] returns, and drops the journal
     * files so SQLite reads only the rewritten file
     */
    private fun damage(change: (ByteArray) -> ByteArray) {
        databaseFile.writeBytes(change(databaseFile.readBytes()))
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
    }

    /**
     * Asserts that SQLite itself rejects the damaged file and that the file is still there, so a
     * test cannot pass by damaging nothing or by deleting the file before the store opens it.
     *
     * SQLite's default error handler deletes a file it finds corrupt, so the check opens it with a
     * handler that leaves the file alone.
     */
    private fun assertDamagedFileIsRejectedAndKept() {
        val keepFile = DatabaseErrorHandler { }
        val rejected = try {
            SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
                keepFile
            ).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    !(cursor.moveToFirst() && cursor.getString(0) == "ok")
                }
            }
        } catch (e: SQLiteException) {
            true
        }

        assertTrue("SQLite accepted a file that should be damaged", rejected)
        assertTrue("checking the file must not delete it", databaseFile.exists())
    }

    private fun insertsAfterDamage(): InboxInsertResult = runBlocking {
        dataSource().insert(message("after-damage"), LIMIT)
    }

    @Test
    fun aFileOverwrittenWithGarbageIsReplacedAndAcceptsMessages() {
        seed(SEEDED)

        damage { bytes -> bytes.also { it.fill(GARBAGE) } }

        assertDamagedFileIsRejectedAndKept()
        assertEquals(InboxInsertResult.INSERTED, insertsAfterDamage())
    }

    @Test
    fun aDamagedHeaderIsReplacedAndAcceptsMessages() {
        seed(SEEDED)

        damage { bytes -> bytes.also { repeat(HEADER_BYTES) { i -> it[i] = GARBAGE } } }

        assertDamagedFileIsRejectedAndKept()
        assertEquals(InboxInsertResult.INSERTED, insertsAfterDamage())
    }

    @Test
    fun aTruncatedFileIsReplacedAndAcceptsMessages() {
        seed(SEEDED)

        damage { bytes -> bytes.copyOf(HEADER_BYTES) }

        assertDamagedFileIsRejectedAndKept()
        assertEquals(InboxInsertResult.INSERTED, insertsAfterDamage())
    }

    @Test
    fun anEmptyFileIsAValidEmptyDatabaseThatAcceptsMessages() {
        seed(SEEDED)

        damage { ByteArray(0) }

        assertEquals(InboxInsertResult.INSERTED, insertsAfterDamage())
    }

    @Test
    fun aCorruptDataPageIsReplacedAndPreviousMessagesAreGone() {
        seed(SEEDED)

        damage { bytes ->
            bytes.also {
                for (i in maxOf(PAGE_BYTES, it.size - PAGE_BYTES) until it.size) it[i] = GARBAGE
            }
        }

        assertDamagedFileIsRejectedAndKept()
        val source = dataSource()
        runBlocking {
            assertEquals(InboxInsertResult.INSERTED, source.insert(message("after-damage"), LIMIT))
            assertEquals(InboxInsertResult.INSERTED, source.insert(message("seed-0"), LIMIT))
        }
    }

    @Test
    fun aDatabaseFromANewerSchemaVersionIsRecreatedWithoutAMigration() {
        seed(SEEDED)
        stampSchemaVersion(InboxDatabase.VERSION + 1)

        val source = dataSource()

        runBlocking {
            assertEquals(InboxInsertResult.INSERTED, source.insert(message("seed-0"), LIMIT))
        }
    }

    @Test
    fun aDatabaseOfTheCurrentVersionKeepsItsMessages() {
        seed(SEEDED)

        val source = dataSource()

        runBlocking {
            assertEquals(InboxInsertResult.DUPLICATE, source.insert(message("seed-0"), LIMIT))
        }
        assertTrue(databaseFile.exists())
    }

    @Test
    fun aStoreThatCannotBeOpenedForWritingReportsFailedKeepsItsMessagesAndRecoversWhenAccessReturns() {
        seed(SEEDED)
        val repository = KlaviyoInboxRepository(dataSource())
        val directory = databaseFile.parentFile ?: error("database directory missing")
        // Read-only files and directory: opening for write fails the way a full or locked disk does
        val files = directory.listFiles { file -> file.name.startsWith(databaseName) }.orEmpty()

        try {
            files.forEach { it.setWritable(false, false) }
            directory.setWritable(false, false)

            assertEquals(
                InboxCaptureResult.FAILED,
                runBlocking { repository.capture(message("denied")) }
            )
        } finally {
            directory.setWritable(true, false)
            files.forEach { it.setWritable(true, false) }
        }

        assertEquals(
            InboxCaptureResult.CAPTURED,
            runBlocking { repository.capture(message("restored")) }
        )
        assertEquals(
            "a transient failure must not delete stored messages",
            InboxCaptureResult.DUPLICATE,
            runBlocking { repository.capture(message("seed-0")) }
        )
    }

    @Test
    fun aWriteThatFailsOnAnOpenStoreReportsFailedAndTheMessagesSurviveARestart() {
        seed(SEEDED)
        val source = dataSource()
        val repository = KlaviyoInboxRepository(source)
        assertEquals(
            InboxCaptureResult.CAPTURED,
            runBlocking { repository.capture(message("opened")) }
        )

        // Closing the open database under the repository makes the next insert throw
        source.close()

        assertEquals(
            InboxCaptureResult.FAILED,
            runBlocking { repository.capture(message("while-failing")) }
        )
        assertEquals(
            "a later capture on a store that stays closed still reports FAILED without throwing",
            InboxCaptureResult.FAILED,
            runBlocking { repository.capture(message("still-failing")) }
        )

        val restarted = KlaviyoInboxRepository(dataSource())
        assertEquals(
            InboxCaptureResult.CAPTURED,
            runBlocking { restarted.capture(message("after-restart")) }
        )
        assertEquals(
            "messages stored before the failure are still there",
            InboxCaptureResult.DUPLICATE,
            runBlocking { restarted.capture(message("opened")) }
        )
    }

    /**
     * Marks the database as written by a newer SDK, the state left after a host app rolls back to
     * an older release
     */
    private fun stampSchemaVersion(version: Int) {
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { it.version = version }
    }

    private companion object {
        const val COMPANY = "company"
        const val LIMIT = 100
        const val SEEDED = 40
        const val HEADER_BYTES = 100
        const val PAGE_BYTES = 4096
        const val GARBAGE: Byte = 0x5A
    }
}
