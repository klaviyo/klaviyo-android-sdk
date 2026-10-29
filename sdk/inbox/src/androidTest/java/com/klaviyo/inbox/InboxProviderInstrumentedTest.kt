package com.klaviyo.inbox

import android.content.Context
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The public entry points against a configured SDK and a real Room store, so the wiring from
 * [Klaviyo.mobileInbox] through the provider and repository to the database is covered together
 */
class InboxProviderInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "inbox-provider-test-${UUID.randomUUID()}.db"
    private val dataSource = RoomInboxDataSource(context, databaseName)

    @Before
    fun setup() {
        context.deleteDatabase(databaseName)
        configure(COMPANY)
        Registry.register<InboxRepository>(KlaviyoInboxRepository(dataSource))
        InboxInitProvider().onCreate()
    }

    @After
    fun cleanup() {
        Registry.unregister<MobileInboxProvider>()
        Registry.unregister<InboxRepository>()
        Registry.unregister<Config>()
        dataSource.close()
        context.deleteDatabase(databaseName)
    }

    private fun configure(apiKey: String) = Registry.register<Config>(
        Registry.configBuilder.apiKey(apiKey).applicationContext(context).build()
    )

    private fun message(id: String, companyId: String, isRead: Boolean = false) = InboxMessageEntity(
        transmissionId = id,
        companyId = companyId,
        title = "Title $id",
        body = null,
        defaultDestination = InboxRecordDestination("open_app", null),
        mediaUrl = null,
        mediaType = null,
        customData = emptyMap(),
        actions = emptyList(),
        sentAt = null,
        collapseId = null,
        badge = null,
        receivedAt = 1_000L,
        attributionJson = null,
        isRead = isRead
    )

    private fun seed() = runBlocking {
        dataSource.insert(message("a1", COMPANY), LIMIT)
        dataSource.insert(message("a2", COMPANY), LIMIT)
        dataSource.insert(message("a3", COMPANY, isRead = true), LIMIT)
        dataSource.insert(message("b1", OTHER_COMPANY), LIMIT)
        dataSource.insert(message("b2", OTHER_COMPANY), LIMIT)
        dataSource.insert(message("b3", OTHER_COMPANY), LIMIT)
    }

    @Test
    fun unreadCountThroughTheEntryPointIsScopedToTheConfiguredCompany() = runBlocking {
        seed()

        assertEquals(2, Klaviyo.mobileInbox.fetchUnreadCount())
    }

    @Test
    fun unreadCountFollowsTheCurrentApiKey() = runBlocking {
        seed()

        configure(OTHER_COMPANY)

        assertEquals(3, Klaviyo.mobileInbox.fetchUnreadCount())
    }

    @Test
    fun unreadCountIsZeroBeforeAnythingIsCaptured() = runBlocking {
        assertEquals(0, Klaviyo.mobileInbox.fetchUnreadCount())
    }

    private fun seedNumbered(count: Int) = runBlocking {
        (1..count).forEach {
            dataSource.insert(
                message("m$it", COMPANY).copy(sentAt = it * 100L, sortTime = it * 100L),
                LIMIT
            )
        }
        dataSource.insert(message("other", OTHER_COMPANY), LIMIT)
    }

    @Test
    fun pagesThroughTheEntryPointFollowTheCursorAndStayInTheConfiguredCompany() = runBlocking {
        seedNumbered(5)

        val first = Klaviyo.mobileInbox.fetchPage(InboxQuery(pageSize = 2))
        val second = Klaviyo.mobileInbox.fetchPage(
            InboxQuery(pageSize = 2, cursor = first.nextCursor)
        )
        val third = Klaviyo.mobileInbox.fetchPage(
            InboxQuery(pageSize = 2, cursor = second.nextCursor)
        )

        assertEquals(listOf("m5", "m4"), first.messages.map { it.id })
        assertEquals(listOf("m3", "m2"), second.messages.map { it.id })
        assertEquals(listOf("m1"), third.messages.map { it.id })
        assertNull(third.nextCursor)
    }

    @Test
    fun aPageIsEmptyBeforeAnythingIsCaptured() = runBlocking {
        val page = Klaviyo.mobileInbox.fetchPage()

        assertTrue(page.messages.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun aGarbageCursorIsRejectedThroughTheEntryPoint() = runBlocking {
        val error = try {
            Klaviyo.mobileInbox.fetchPage(InboxQuery(cursor = InboxCursor("garbage")))
            null
        } catch (e: InboxException) {
            e
        }

        assertEquals(InboxException.Reason.INVALID_CURSOR, error?.reason)
    }

    @Test
    fun javaCallbackReceivesAPageOnTheMainThread() {
        seedNumbered(3)
        val delivered = CountDownLatch(1)
        var result: InboxResult<InboxPage>? = null
        var onMainThread = false

        KlaviyoInbox.fetchPage(InboxQuery(pageSize = 2)) {
            onMainThread = Looper.myLooper() == Looper.getMainLooper()
            result = it
            delivered.countDown()
        }

        assertTrue(
            "callback was not called",
            delivered.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )
        assertTrue("callback was not on the main thread", onMainThread)
        val page = (result as? InboxResult.Success<InboxPage>)?.value
        assertEquals(listOf("m3", "m2"), page?.messages?.map { it.id })
    }

    @Test
    fun javaCallbackReceivesTheCountOnTheMainThread() {
        seed()
        val delivered = CountDownLatch(1)
        var result: InboxResult<Int>? = null
        var onMainThread = false

        KlaviyoInbox.fetchUnreadCount {
            onMainThread = Looper.myLooper() == Looper.getMainLooper()
            result = it
            delivered.countDown()
        }

        assertTrue(
            "callback was not called",
            delivered.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )
        assertTrue("callback was not on the main thread", onMainThread)
        val success = result as? InboxResult.Success<Int>
        assertEquals(2, success?.value)
    }

    private companion object {
        const val COMPANY = "company"
        const val OTHER_COMPANY = "other-company"
        const val LIMIT = 100
        const val CALLBACK_TIMEOUT_SECONDS = 5L
    }
}
