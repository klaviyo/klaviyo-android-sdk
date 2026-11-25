package com.klaviyo.inbox

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChangeObservers
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.model.DataStore
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.PushNotificationDispatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class InboxInitProviderTest : BaseTest() {

    private val pushDispatcher = mockk<PushNotificationDispatcher>(relaxed = true)

    /**
     * The provider's application context, before the SDK config exists
     */
    private val providerContext = mockk<Context>().apply {
        every { applicationContext } returns this
    }

    private fun provider() = inboxInitProvider(providerContext)

    @Before
    override fun setup() {
        super.setup()
        Registry.register<PushNotificationDispatcher>(pushDispatcher)
    }

    @After
    override fun cleanup() {
        Registry.unregister<PushNotificationDispatcher>()
        Registry.unregister<InboxRepository>()
        Registry.unregister<InboxResetGate>()
        Registry.unregister<MobileInboxProvider>()
        Registry.unregister<StateChangeObservers>()
        Registry.unregister<DataStore>()
        Registry.unregister<State>()
        Registry.unregister<Config>()
        Registry.unregister<ApiClient>()
        super.cleanup()
    }

    @Test
    fun `onCreate registers the repository without storage access`() = mockkStatic(Room::class) {
        provider().onCreate()

        assertTrue(Registry.isRegistered<InboxRepository>())
        verify(exactly = 0) { Room.databaseBuilder(any(), any<Class<InboxDatabase>>(), any()) }
        verify(exactly = 0) { providerContext.getSharedPreferences(any(), any()) }
        verify(exactly = 0) { mockConfig.applicationContext }
    }

    @Test
    fun `onCreate does not read the SDK config, which is absent before initialize`() {
        every { Registry.config } throws MissingConfig()

        provider().onCreate()

        assertTrue(Registry.isRegistered<InboxRepository>())
        verify { pushDispatcher.onKlaviyoNotification(any<InboxCaptureObserver>()) }
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `onCreate leaves capture off and logs when there is no context`() {
        assertTrue(inboxInitProvider(context = null).onCreate())

        assertFalse(Registry.isRegistered<InboxRepository>())
        verify(exactly = 0) { pushDispatcher.onKlaviyoNotification(any()) }
        verify { spyLog.error(any(), any<IllegalStateException>()) }
    }

    @Test
    fun `onCreate keeps a repository that is already registered`() {
        val existing = mockk<InboxRepository>()
        Registry.register<InboxRepository>(existing)

        provider().onCreate()

        assertSame(existing, Registry.getOrNull<InboxRepository>())
    }

    @Test
    fun `onCreate makes the public inbox available`() {
        provider().onCreate()

        assertTrue(Registry.getOrNull<MobileInboxProvider>() is KlaviyoMobileInboxProvider)
    }

    @Test
    fun `onCreate registers the capture observer with push`() {
        provider().onCreate()

        verify { pushDispatcher.onKlaviyoNotification(any<InboxCaptureObserver>()) }
    }

    @Test
    fun `onCreate warns when push is unavailable`() {
        Registry.unregister<PushNotificationDispatcher>()

        assertTrue(provider().onCreate())
        verify { spyLog.warning(any(), null) }
    }

    @Test
    fun `the reset observer clears the registered repository`() = runTest(dispatcher) {
        val dataSource = FakeInboxDataSource()
        Registry.register<InboxResetGate>(inboxResetGate(dataSource, dispatcher = dispatcher))
        val repository = gatedRepository(dataSource, { Registry.get<InboxResetGate>() })
        Registry.register<InboxRepository>(repository)
        dataSource.messages["a-1"] = inboxMessageEntity("a-1", API_KEY)

        provider().onCreate()
        initializeAnalytics()
        Klaviyo.resetProfile()
        advanceUntilIdle()

        assertSame(repository, Registry.getOrNull<InboxRepository>())
        assertTrue(dataSource.messages.isEmpty())
        assertEquals(listOf("clearAll"), dataSource.calls)
    }

    @Test
    fun `Inbox started before initialize records only the explicit reset before the call returns`() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every {
            providerContext.getSharedPreferences(
                "KlaviyoSDKPreferences",
                Context.MODE_PRIVATE
            )
        } returns preferences
        every { preferences.getString(any(), any()) } returns null
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        var externalIdAtCommit: String? = EXTERNAL_ID
        every { editor.commit() } answers {
            externalIdAtCommit = Registry.get<State>().externalId
            true
        }
        assertFalse(Registry.isRegistered<State>())
        provider().onCreate()
        assertTrue(Registry.isRegistered<StateChangeObservers>())
        val observers = Registry.get<StateChangeObservers>()
        provider().onCreate()
        assertSame(observers, Registry.get<StateChangeObservers>())
        assertFalse(Registry.isRegistered<State>())

        initializeAnalytics()
        Klaviyo.setProfile(Profile(email = EMAIL))
        Klaviyo.setProfile(Profile(externalId = EXTERNAL_ID))
        assertEquals(EXTERNAL_ID, Registry.get<State>().externalId)
        verify(exactly = 0) { editor.commit() }

        Klaviyo.resetProfile()

        verify(exactly = 1) { editor.commit() }
        assertNull(externalIdAtCommit)
    }

    private fun initializeAnalytics() {
        every { mockContext.applicationContext } returns mockk<Application>(relaxed = true)
        every { Registry.configBuilder } returns mockk<Config.Builder>().apply {
            every { apiKey(any()) } returns this
            every { applicationContext(any()) } returns this
            every { build() } returns mockConfig
        }
        Registry.register<ApiClient>(mockk<ApiClient>(relaxed = true))
        Klaviyo.initialize(API_KEY, mockContext)
    }
}
