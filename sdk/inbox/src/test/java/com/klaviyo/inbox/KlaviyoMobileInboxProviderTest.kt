package com.klaviyo.inbox

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class KlaviyoMobileInboxProviderTest : BaseTest() {

    private val provider = KlaviyoMobileInboxProvider()

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
        every { mockContext.deleteDatabase(any()) } returns true
        Registry.register<MobileInboxProvider>(provider)
    }

    @After
    override fun cleanup() {
        Registry.unregister<MobileInboxProvider>()
        unmockDeviceProperties()
        super.cleanup()
    }

    private fun storedSettings() = MobileInboxSettings.load()

    private fun deleteStoreJobs() = dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `never registered has no settings and capture is disabled`() {
        assertNull(storedSettings())
        assertFalse(isInboxCaptureEnabled())
    }

    @Test
    fun `register persists enabled settings with the config`() {
        Klaviyo.registerForMobileInbox(MobileInboxConfig(250))

        assertEquals(
            MobileInboxSettings(enabled = true, localRetentionLimit = 250),
            storedSettings()
        )
        assertTrue(isInboxCaptureEnabled())
    }

    @Test
    fun `register with default config persists the default retention limit`() {
        Klaviyo.registerForMobileInbox()

        assertEquals(
            MobileInboxConfig.DEFAULT_LOCAL_RETENTION_LIMIT,
            storedSettings()?.localRetentionLimit
        )
    }

    @Test
    fun `registering again replaces the stored config`() {
        Klaviyo.registerForMobileInbox(MobileInboxConfig(250))
        Klaviyo.registerForMobileInbox(MobileInboxConfig(50))

        assertEquals(50, storedSettings()?.localRetentionLimit)
    }

    @Test
    fun `register does not create or delete the inbox store`() {
        Klaviyo.registerForMobileInbox()
        deleteStoreJobs()

        verify(exactly = 0) { mockContext.deleteDatabase(any()) }
    }

    @Test
    fun `registration persists across provider instances`() {
        Klaviyo.registerForMobileInbox(MobileInboxConfig(250))

        // A new process reads the same persisted settings
        Registry.register<MobileInboxProvider>(KlaviyoMobileInboxProvider())

        assertEquals(
            MobileInboxSettings(enabled = true, localRetentionLimit = 250),
            storedSettings()
        )
        assertTrue(isInboxCaptureEnabled())
    }

    @Test
    fun `unregister persists disabled settings and keeps the retention limit`() {
        Klaviyo.registerForMobileInbox(MobileInboxConfig(250))
        Klaviyo.unregisterFromMobileInbox()

        assertEquals(
            MobileInboxSettings(enabled = false, localRetentionLimit = 250),
            storedSettings()
        )
        assertFalse(isInboxCaptureEnabled())
    }

    @Test
    fun `unregister deletes the inbox store after persisting the disabled flag`() {
        Klaviyo.registerForMobileInbox()
        Klaviyo.unregisterFromMobileInbox()
        deleteStoreJobs()

        verifyOrder {
            spyDataStore.store(
                MobileInboxSettings.STORAGE_KEY,
                match { it.contains("\"enabled\":false") }
            )
            mockContext.deleteDatabase(InboxStore.DATABASE_NAME)
        }
    }

    @Test
    fun `unregister without a prior registration disables capture and deletes any store`() {
        Klaviyo.unregisterFromMobileInbox()
        deleteStoreJobs()

        assertEquals(false, storedSettings()?.enabled)
        verify(exactly = 1) { mockContext.deleteDatabase(InboxStore.DATABASE_NAME) }
    }

    @Test
    fun `capture is disabled when notification permission is not granted`() {
        every { DeviceProperties.notificationPermissionGranted } returns false
        Klaviyo.registerForMobileInbox()

        assertFalse(isInboxCaptureEnabled())
    }

    @Test
    fun `unreadable settings are treated as disabled`() {
        spyDataStore.store(MobileInboxSettings.STORAGE_KEY, "not json")

        assertEquals(false, storedSettings()?.enabled)
        assertFalse(isInboxCaptureEnabled())
        verify { spyLog.warning(any(), any()) }
    }

    @Test
    fun `stored retention limit outside the supported range is clamped on load`() {
        spyDataStore.store(
            MobileInboxSettings.STORAGE_KEY,
            "{\"enabled\":true,\"local_retention_limit\":9999}"
        )

        assertEquals(
            MobileInboxConfig.MAX_LOCAL_RETENTION_LIMIT,
            storedSettings()?.localRetentionLimit
        )
    }

    @Test
    fun `capture gate returns false before initialize`() {
        every { Registry.dataStore } throws MissingConfig()

        assertFalse(isInboxCaptureEnabled())
    }

    @Test
    fun `register before initialize is caught and logged`() {
        every { Registry.dataStore } throws MissingConfig()

        Klaviyo.registerForMobileInbox()

        verify { spyLog.error(any(), any()) }
    }
}
