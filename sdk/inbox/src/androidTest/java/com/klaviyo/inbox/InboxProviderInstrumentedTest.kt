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
