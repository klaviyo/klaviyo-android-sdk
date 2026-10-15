package com.klaviyo.forms

import com.klaviyo.fixtures.BaseTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class InAppFormsConfigTest : BaseTest() {
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

    @Test
    fun `test InAppFormsConfig has no preview token by default`() {
        assertNull(InAppFormsConfig().previewToken)
        assertNull(InAppFormsConfig(10).previewToken)
        assertNull(InAppFormsConfig(10.seconds).previewToken)
    }

    @Test
    fun `test InAppFormsConfig carries a preview token`() {
        val token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123-_6"

        InAppFormsConfig(token).run {
            assertEquals(token, previewToken)
            assertEquals(3600L, getSessionTimeoutDuration().inWholeSeconds)
        }
        InAppFormsConfig(10, token).run {
            assertEquals(token, previewToken)
            assertEquals(10L, getSessionTimeoutDuration().inWholeSeconds)
        }
        InAppFormsConfig(10.seconds, token).run {
            assertEquals(token, previewToken)
            assertEquals(10L, getSessionTimeoutDuration().inWholeSeconds)
        }
    }

    @Test
    fun `test InAppFormsConfig toString redacts the preview token`() {
        val token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123-_6"
        assertFalse(InAppFormsConfig(token).toString().contains(token))
    }
}
