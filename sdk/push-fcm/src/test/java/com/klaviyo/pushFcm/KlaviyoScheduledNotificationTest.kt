package com.klaviyo.pushFcm

import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class KlaviyoScheduledNotificationTest : BaseTest() {
    private val testTag = "test_notification_tag"
    private val testTime = 1714649302000L // 2024-05-02T12:01:42Z
    private val testData = mapOf(
        "_k" to "value",
        "title" to "Test Notification",
        "body" to "This is a test notification"
    )

    @Before
    override fun setup() {
        super.setup() // This will mock Registry and other dependencies
    }

    @Test
    fun `Test storing notification data`() {
        val keySlot = slot<String>()
        val valueSlot = slot<String>()

        every { spyDataStore.store(capture(keySlot), capture(valueSlot)) } returns Unit

        val result = KlaviyoScheduledNotification.storeNotification(
            tag = testTag,
            data = testData,
            scheduledTime = testTime
        )

        assert(result)
        assert(keySlot.captured.contains(testTag))
        assert(valueSlot.captured.contains(testTag))
        assert(valueSlot.captured.contains(testTime.toString()))
    }

    @Test
    fun `Test retrieving stored notification`() {
        val expectedJsonString = """
            {"tag":"$testTag","scheduledTime":$testTime,"data":{"_k":"value","title":"Test Notification","body":"This is a test notification"}}
        """.trimIndent()

        every { spyDataStore.fetch(any()) } returns expectedJsonString

        val notificationData = KlaviyoScheduledNotification.getNotification(testTag)

        assert(notificationData != null)
        assert(notificationData?.tag == testTag)
        assert(notificationData?.scheduledTime == testTime)
        assert(notificationData?.data == testData)
    }

    @Test
    fun `Test retrieving non-existent notification`() {
        every { spyDataStore.fetch(any()) } returns null

        val notificationData = KlaviyoScheduledNotification.getNotification(testTag)

        assert(notificationData == null)
    }

    @Test
    fun `Test removing notification`() {
        val keySlot = slot<String>()

        every { spyDataStore.clear(capture(keySlot)) } returns Unit

        KlaviyoScheduledNotification.removeNotification(testTag)

        verify(exactly = 1) { spyDataStore.clear(any()) }
        assert(keySlot.captured.contains(testTag))
    }
}
