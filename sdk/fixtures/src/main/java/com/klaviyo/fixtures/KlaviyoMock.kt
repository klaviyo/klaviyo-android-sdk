package com.klaviyo.fixtures

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.ProfileKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.Serializable

/**
 * Utility class for mocking Klaviyo singleton from Java tests.
 *
 * Since Klaviyo is a Kotlin object (singleton), it can only be mocked using MockK's
 * mockkObject function, which is only available in Kotlin. This class provides a bridge
 * for Java tests to mock and verify Klaviyo method calls.
 */
object KlaviyoMock {

    // Mock objects exposed for Java tests to use
    @JvmStatic
    lateinit var mockContext: Context
        private set

    @JvmStatic
    lateinit var mockIntent: Intent
        private set

    @JvmStatic
    lateinit var mockUri: Uri
        private set

    /**
     * Sets up mocks for all Klaviyo public API methods.
     * Call this in @Before setup methods.
     */
    @JvmStatic
    fun setup() {
        // Create mock objects
        mockContext = mockk(relaxed = true)
        mockIntent = mockk(relaxed = true)
        mockUri = mockk(relaxed = true)

        mockkObject(Klaviyo)

        // Mock all public methods to return Klaviyo for chaining
        every { Klaviyo.initialize(any(), any()) } returns Klaviyo
        every { Klaviyo.registerForLifecycleCallbacks(any()) } returns Klaviyo
        every { Klaviyo.setProfile(any()) } returns Klaviyo
        every { Klaviyo.setEmail(any()) } returns Klaviyo
        every { Klaviyo.setPhoneNumber(any()) } returns Klaviyo
        every { Klaviyo.setExternalId(any()) } returns Klaviyo
        every { Klaviyo.setPushToken(any()) } returns Klaviyo
        every { Klaviyo.setProfileAttribute(any(), any()) } returns Klaviyo
        every { Klaviyo.resetProfile() } returns Klaviyo
        every { Klaviyo.createEvent(any<Event>()) } returns Klaviyo
        every { Klaviyo.createEvent(any<EventMetric>(), any()) } returns Klaviyo
        every { Klaviyo.handlePush(any()) } returns Klaviyo
        every { Klaviyo.registerDeepLinkHandler(any()) } returns Klaviyo
        every { Klaviyo.unregisterDeepLinkHandler() } returns Klaviyo

        // Mock getters to return test values
        every { Klaviyo.getEmail() } returns "test@example.com"
        every { Klaviyo.getPhoneNumber() } returns "+15555555555"
        every { Klaviyo.getExternalId() } returns "ext-123"
        every { Klaviyo.getPushToken() } returns "push-token-abc"

        // Mock boolean methods
        every { Klaviyo.handleUniversalTrackingLink(any<String>()) } returns true
        every { Klaviyo.handleUniversalTrackingLink(any<Intent>()) } returns true

        // Extension properties defined inside Klaviyo object - accessed via run block
        every { Klaviyo.run { any<Intent>().isKlaviyoIntent } } returns true
        every { Klaviyo.run { any<Intent>().isKlaviyoNotificationIntent } } returns true
        every { Klaviyo.run { any<Intent>().isKlaviyoUniversalTrackingIntent } } returns true
        every { Klaviyo.run { any<Uri>().isKlaviyoUniversalTrackingUri } } returns true
    }

    /**
     * Cleans up mocks.
     * Call this in @After teardown methods.
     */
    @JvmStatic
    fun teardown() {
        unmockkObject(Klaviyo)
    }

    // Verification methods for Java tests

    @JvmStatic
    fun verifyInitializeCalled(apiKey: String) {
        verify { Klaviyo.initialize(apiKey, any()) }
    }

    @JvmStatic
    fun verifyRegisterForLifecycleCallbacksCalled() {
        verify { Klaviyo.registerForLifecycleCallbacks(any()) }
    }

    @JvmStatic
    fun verifySetProfileCalled() {
        verify { Klaviyo.setProfile(any()) }
    }

    @JvmStatic
    fun verifySetEmailCalled(email: String) {
        verify { Klaviyo.setEmail(email) }
    }

    @JvmStatic
    fun verifySetPhoneNumberCalled(phone: String) {
        verify { Klaviyo.setPhoneNumber(phone) }
    }

    @JvmStatic
    fun verifySetExternalIdCalled(externalId: String) {
        verify { Klaviyo.setExternalId(externalId) }
    }

    @JvmStatic
    fun verifySetPushTokenCalled(token: String) {
        verify { Klaviyo.setPushToken(token) }
    }

    @JvmStatic
    fun verifySetProfileAttributeCalled(key: ProfileKey, value: Serializable) {
        verify { Klaviyo.setProfileAttribute(key, value) }
    }

    @JvmStatic
    fun verifyResetProfileCalled() {
        verify { Klaviyo.resetProfile() }
    }

    @JvmStatic
    fun verifyCreateEventCalled() {
        verify { Klaviyo.createEvent(any<Event>()) }
    }

    @JvmStatic
    fun verifyCreateEventWithMetricCalled(metric: EventMetric) {
        verify { Klaviyo.createEvent(metric, any()) }
    }

    @JvmStatic
    fun verifyHandlePushCalled() {
        verify { Klaviyo.handlePush(any()) }
    }

    @JvmStatic
    fun verifyHandleUniversalTrackingLinkStringCalled(url: String) {
        verify { Klaviyo.handleUniversalTrackingLink(url) }
    }

    @JvmStatic
    fun verifyHandleUniversalTrackingLinkIntentCalled() {
        verify { Klaviyo.handleUniversalTrackingLink(any<Intent>()) }
    }

    @JvmStatic
    fun verifyRegisterDeepLinkHandlerCalled() {
        verify { Klaviyo.registerDeepLinkHandler(any()) }
    }

    @JvmStatic
    fun verifyUnregisterDeepLinkHandlerCalled() {
        verify { Klaviyo.unregisterDeepLinkHandler() }
    }

    @JvmStatic
    fun verifyIsKlaviyoIntentCalled() {
        verify { Klaviyo.run { any<Intent>().isKlaviyoIntent } }
    }

    @JvmStatic
    fun verifyIsKlaviyoNotificationIntentCalled() {
        verify { Klaviyo.run { any<Intent>().isKlaviyoNotificationIntent } }
    }

    @JvmStatic
    fun verifyIsKlaviyoUniversalTrackingIntentCalled() {
        verify { Klaviyo.run { any<Intent>().isKlaviyoUniversalTrackingIntent } }
    }

    @JvmStatic
    fun verifyIsKlaviyoUniversalTrackingUriCalled() {
        verify { Klaviyo.run { any<Uri>().isKlaviyoUniversalTrackingUri } }
    }
}
