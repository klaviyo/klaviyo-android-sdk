package com.klaviyo.forms

import com.klaviyo.fixtures.BaseTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InAppFormsConfigTest : BaseTest() {
    @Test
    fun `load timeout accepts finite positive milliseconds and otherwise falls back`() {
        assertNull(InAppFormsConfig().getLoadTimeout())
        assertEquals(30.seconds, InAppFormsConfig(loadTimeout = 30.seconds).getLoadTimeout())
        listOf(0.seconds, (-1).seconds, Duration.INFINITE, Long.MAX_VALUE.seconds).forEach {
            assertNull(InAppFormsConfig(loadTimeout = it).getLoadTimeout())
        }
    }

    @Test
    fun `replay history is bounded and negative windows disable history`() {
        assertEquals(10.seconds, InAppFormsConfig().getEventReplayWindow())
        listOf(0.seconds, (-1).seconds).forEach {
            assertEquals(0.seconds, InAppFormsConfig(eventReplayWindow = it).getEventReplayWindow())
        }
        listOf(60.seconds, Duration.INFINITE).forEach {
            assertEquals(
                30.seconds,
                InAppFormsConfig(eventReplayWindow = it).getEventReplayWindow()
            )
        }
    }

    @Test
    fun `legacy copy keeps configured loading and replay values`() {
        val original = InAppFormsConfig(
            20.seconds,
            loadTimeout = 30.seconds,
            eventReplayWindow = 25.seconds
        )
        val copy = original.copy(sessionTimeoutDuration = 40.seconds)
        assertEquals(40.seconds, copy.getSessionTimeoutDuration())
        assertEquals(30.seconds, copy.getLoadTimeout())
        assertEquals(25.seconds, copy.getEventReplayWindow())
        assertEquals(original, original.copy())
    }

    @Test
    fun `test InAppFormsConfig default values`() = assertEquals(
        3600L,
        InAppFormsConfig().getSessionTimeoutDuration().inWholeSeconds
    )

    @Test
    fun `test InAppFormsConfig custom values`() = assertEquals(
        10_000L,
        InAppFormsConfig(10.seconds).getSessionTimeoutDuration().inWholeMilliseconds
    )

    @Test
    fun `test InAppFormsConfig negative value`() = assertEquals(
        0L,
        InAppFormsConfig((-10).seconds).getSessionTimeoutDuration().inWholeMilliseconds
    )

    @Test
    fun `test InAppFormsConfig max value`() = assertEquals(
        Long.MAX_VALUE,
        InAppFormsConfig(Long.MAX_VALUE.seconds).getSessionTimeoutDuration().inWholeMilliseconds
    )

    @Test
    fun `test InAppFormsConfig infinite value`() = assertEquals(
        Long.MAX_VALUE,
        InAppFormsConfig(Duration.INFINITE).getSessionTimeoutDuration().inWholeMilliseconds
    )
}
