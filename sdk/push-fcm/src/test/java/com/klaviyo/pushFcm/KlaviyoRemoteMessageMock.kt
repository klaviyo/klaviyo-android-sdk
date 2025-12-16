package com.klaviyo.pushFcm

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify

/**
 * Utility class for mocking KlaviyoRemoteMessage extension functions from Java tests.
 *
 * Since KlaviyoRemoteMessage is a Kotlin object containing extension properties and functions
 * on RemoteMessage, Java callers must use KlaviyoRemoteMessage.INSTANCE.xxx(message) syntax.
 * This class provides a bridge for Java tests to mock and verify these method calls.
 */
object KlaviyoRemoteMessageMock {

    @JvmStatic
    lateinit var mockRemoteMessage: RemoteMessage
        private set

    @JvmStatic
    lateinit var mockContext: Context
        private set

    @JvmStatic
    lateinit var mockIntent: Intent
        private set

    /**
     * Sets up mocks for KlaviyoRemoteMessage extension properties.
     * Call this in @Before setup methods.
     */
    @JvmStatic
    fun setup() {
        mockRemoteMessage = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockIntent = mockk(relaxed = true)

        mockkObject(KlaviyoRemoteMessage)

        // Mock extension properties - accessed via KlaviyoRemoteMessage object
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.isKlaviyoMessage } } returns true
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.isKlaviyoNotification } } returns true
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.title } } returns "Test Title"
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.body } } returns "Test Body"
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.channel_id } } returns "test-channel"
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.channel_name } } returns "Test Channel"
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.channel_description } } returns "Test Description"
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.channel_importance } } returns 3
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.notificationPriority } } returns 0
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.notificationTag } } returns "test-tag"
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.notificationCount } } returns 1
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.deepLink } } returns mockk<Uri>()
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.sound } } returns mockk<Uri>()
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.hasKlaviyoKeyValuePairs } } returns true
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.keyValuePairs } } returns mapOf(
            "key" to "value"
        )

        // Mock functions
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.getSmallIcon(any()) } } returns android.R.drawable.sym_def_app_icon
        every { KlaviyoRemoteMessage.run { mockRemoteMessage.getColor(any()) } } returns 0xFF0000

        // Mock Intent extension function
        every { KlaviyoRemoteMessage.run { any<Intent>().appendKlaviyoExtras(any()) } } returns mockIntent
    }

    /**
     * Cleans up mocks.
     * Call this in @After teardown methods.
     */
    @JvmStatic
    fun teardown() {
        unmockkObject(KlaviyoRemoteMessage)
    }

    // Verification methods

    @JvmStatic
    fun verifyIsKlaviyoMessageCalled() {
        verify { KlaviyoRemoteMessage.run { mockRemoteMessage.isKlaviyoMessage } }
    }

    @JvmStatic
    fun verifyGetTitleCalled() {
        verify { KlaviyoRemoteMessage.run { mockRemoteMessage.title } }
    }

    @JvmStatic
    fun verifyGetSmallIconCalled() {
        verify { KlaviyoRemoteMessage.run { mockRemoteMessage.getSmallIcon(any()) } }
    }
}
