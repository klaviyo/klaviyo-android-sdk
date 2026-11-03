package com.klaviyo.inbox

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * A push delivered before the host calls `Klaviyo.initialize` is captured: the company comes from
 * the payload and the context from the process, with no `Config` registered.
 */
class InboxPreInitCaptureInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val databaseName = "inbox-preinit-${UUID.randomUUID()}.db"
    private val dataSource = RoomInboxDataSource(context, databaseName)

    @Before
    fun setup() {
        context.deleteDatabase(databaseName)
        grantNotificationPermission()
        Registry.register<InboxRepository>(KlaviyoInboxRepository(dataSource))
    }

    @After
    fun cleanup() {
        Registry.unregister<InboxRepository>()
        dataSource.close()
        context.deleteDatabase(databaseName)
    }

    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private fun push(company: String?) = RemoteMessage.Builder("to@fcm")
        .setMessageId("fcm-$TRANSMISSION_ID")
        .setData(
            mapOf(
                "title" to "Sale today",
                "body" to "Everything is 20% off",
                "_k" to JSONObject().apply {
                    put("tm", TRANSMISSION_ID)
                    company?.let { put("c", it) }
                }.toString()
            )
        )
        .build()

    @Test
    fun capturesUnderThePayloadCompanyWithNoConfigRegistered() {
        assertFalse(Registry.isRegistered<Config>())

        InboxCaptureObserver(context).onNotification(push(COMPANY))

        assertEquals(listOf(TRANSMISSION_ID), storedIds(COMPANY))
    }

    @Test
    fun skipsAPushWithoutACompany() {
        assertFalse(Registry.isRegistered<Config>())

        InboxCaptureObserver(context).onNotification(push(company = null))

        assertEquals(emptyList<String>(), storedIds(COMPANY))
    }

    private fun storedIds(company: String): List<String> = runBlocking {
        val database = Room.databaseBuilder(
            context,
            InboxDatabase::class.java,
            databaseName
        ).build()
        try {
            database.messageDao().transmissionIdsByCompanyId(company)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val COMPANY = "Abc123"
        const val TRANSMISSION_ID = "tm-preinit"
    }
}
