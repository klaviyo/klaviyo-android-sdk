package com.klaviyo.inbox

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class MobileInboxConfigTest : BaseTest() {
    @Test
    fun `default retention limit is 100`() {
        assertEquals(100, MobileInboxConfig().localRetentionLimit)
        verify(exactly = 0) { spyLog.warning(any(), any()) }
    }

    @Test
    fun `custom retention limit within range is kept`() {
        assertEquals(250, MobileInboxConfig(250).localRetentionLimit)
        assertEquals(1, MobileInboxConfig(1).localRetentionLimit)
        assertEquals(500, MobileInboxConfig(500).localRetentionLimit)
        verify(exactly = 0) { spyLog.warning(any(), any()) }
    }

    @Test
    fun `retention limit above ceiling is clamped with a warning`() {
        assertEquals(500, MobileInboxConfig(2_000).localRetentionLimit)
        verify(exactly = 1) { spyLog.warning(any(), any()) }
    }

    @Test
    fun `retention limit below minimum is clamped with a warning`() {
        assertEquals(1, MobileInboxConfig(0).localRetentionLimit)
        assertEquals(1, MobileInboxConfig(-5).localRetentionLimit)
        verify(exactly = 2) { spyLog.warning(any(), any()) }
    }

    @Test
    fun `configs compare by effective value`() {
        assertEquals(MobileInboxConfig(500), MobileInboxConfig(9_999))
        assertEquals(MobileInboxConfig(500).hashCode(), MobileInboxConfig(9_999).hashCode())
        assertNotEquals(MobileInboxConfig(10), MobileInboxConfig(20))
    }
}
