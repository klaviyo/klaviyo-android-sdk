package com.klaviyo.inbox

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.Build
import android.util.Xml
import androidx.test.platform.app.InstrumentationRegistry
import com.klaviyo.core.Registry
import java.io.File
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

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

    private fun message(id: String) = inboxMessageEntity(id, COMPANY, body = "Body $id")

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
        assertFalse("SQLite accepted a file that should be damaged", databasePassesIntegrityCheck())
        assertTrue("checking the file must not delete it", databaseFile.exists())
    }

    /**
     * Whether SQLite accepts the file as a database, opened with a handler that never deletes it
     */
    private fun databasePassesIntegrityCheck(): Boolean = try {
        SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
            DatabaseErrorHandler { }
        ).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "ok"
            }
        }
    } catch (e: SQLiteException) {
        false
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

        assertEquals("the file must really be empty", 0L, databaseFile.length())
        assertTrue("SQLite must accept an empty file as a database", databasePassesIntegrityCheck())
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
        val repository = gatedRepository(dataSource())
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
        val repository = gatedRepository(source)
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

        val restarted = gatedRepository(dataSource())
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

    @Test
    fun aNewGateClearsAndAcknowledgesAResetPersistedInSharedPreferences() = runBlocking {
        val preferences = context.getSharedPreferences(SDK_PREFERENCES, Context.MODE_PRIVATE)
        val markerKey = PreferencesResetMarkerStore.KEY
        val originalValues = listOf(markerKey, CHECKED_COMMIT_REVISION_KEY).associateWith {
            preferences.getString(it, null)
        }
        val scheduled = mutableListOf<Pair<Job, Runnable>>()
        val pausedDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                scheduled += checkNotNull(context[Job]) to block
            }
        }

        try {
            assertTrue(preferences.edit().remove(markerKey).commit())
            val firstSource = dataSource()
            val inserted = firstSource.insert(message("before-reset"), LIMIT)
            assertEquals(InboxInsertResult.INSERTED, inserted)
            val firstMarkerStore = PreferencesResetMarkerStore(Registry.dataStore(context))
            InboxResetGate(firstSource, firstMarkerStore, pausedDispatcher).requestReset()

            assertEquals(1, scheduled.size)
            assertEquals("1", persistedPreference(markerKey))
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                val revision = checkNotNull(persistedPreference(CHECKED_COMMIT_REVISION_KEY))
                assertNotEquals(originalValues[CHECKED_COMMIT_REVISION_KEY], revision)
            }
            assertEquals(1, firstSource.countUnread(COMPANY))

            val reopenedSource = dataSource()
            val reopenedMarkerStore = PreferencesResetMarkerStore(Registry.dataStore(context))
            val reopenedGate = InboxResetGate(reopenedSource, reopenedMarkerStore)
            assertEquals(1L, reopenedMarkerStore.load())

            assertEquals(0, reopenedGate.withCurrentInbox { reopenedSource.countUnread(COMPANY) })
            assertNull(reopenedMarkerStore.load())
            assertNull(persistedPreference(markerKey))
        } finally {
            scheduled.forEach { (job, block) ->
                job.cancel()
                block.run()
            }
            val editor = preferences.edit()
            originalValues.forEach { (key, value) ->
                if (value == null) editor.remove(key) else editor.putString(key, value)
            }
            assertTrue(editor.commit())
        }
    }

    private fun persistedPreference(key: String): String? {
        val file = File(context.applicationInfo.dataDir, "shared_prefs/$SDK_PREFERENCES.xml")
        return file.inputStream().use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "string" &&
                    parser.getAttributeValue(null, "name") == key
                ) {
                    return@use parser.nextText()
                }
            }
            null
        }
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
        const val SDK_PREFERENCES = "KlaviyoSDKPreferences"
        const val CHECKED_COMMIT_REVISION_KEY = "__klaviyo_checked_commit_revision"
        const val COMPANY = "company"
        const val LIMIT = 100
        const val SEEDED = 40
        const val HEADER_BYTES = 100
        const val PAGE_BYTES = 4096
        const val GARBAGE: Byte = 0x5A
    }
}
