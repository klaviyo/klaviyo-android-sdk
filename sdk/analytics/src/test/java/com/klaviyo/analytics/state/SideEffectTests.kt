package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.PROFILE_ATTRIBUTES
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.ApiObserver
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.ProfileApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SideEffectTests : BaseTest() {

    private val profile = Profile(email = EMAIL)
    private val capturedProfile = slot<Profile>()
    private val capturedApiObserver = slot<ApiObserver>()
    private val capturedStateObserver = slot<StateObserver>()
    private val capturedPushState = slot<String?>()
    private val apiClientMock: ApiClient = mockk<ApiClient>().apply {
        Registry.register<ApiClient>(this)
        every { onApiRequest(any(), capture(capturedApiObserver)) } returns Unit
        every { enqueueProfile(capture(capturedProfile)) } returns Unit
        every { enqueueEvent(any(), any()) } returns Unit
        every { enqueuePushToken(any(), any()) } returns Unit
    }

    private val stateMock = mockk<State>().apply {
        every { onStateChange(capture(capturedStateObserver)) } returns Unit
        every { pushState = captureNullable(capturedPushState) } returns Unit
        every { getAsProfile(withAttributes = any()) } returns profile
        every { resetAttributes() } returns Unit
    }

    @Test
    fun `Subscribes on init`() {
        StateSideEffects(stateMock, apiClientMock)
        verify { stateMock.onStateChange(any()) }
        verify { apiClientMock.onApiRequest(any(), any()) }
    }

    @Test
    fun `Profile changes enqueue a single profile API request`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedStateObserver.captured(ProfileKey.EMAIL, null)
        capturedStateObserver.captured(PROFILE_ATTRIBUTES, null)
        capturedStateObserver.captured(null, null)

        staticClock.execute(debounceTime.toLong())

        verify(exactly = 1) {
            apiClientMock.enqueueProfile(
                match {
                    it.email == profile.email && it.propertyCount() == profile.propertyCount()
                }
            )
        }
    }

    @Test
    fun `Empty attributes do not enqueue a profile API request`() {
        StateSideEffects(
            stateMock.apply {
                every { getAsProfile(withAttributes = any()) } returns Profile(
                    properties = mapOf(ProfileKey.FIRST_NAME to "Kermit")
                )
            },
            apiClientMock
        )

        capturedStateObserver.captured(PROFILE_ATTRIBUTES, null)

        staticClock.execute(debounceTime.toLong())

        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Resetting profile enqueues API call immediately`() {
        StateSideEffects(
            stateMock.apply {
                every { getAsProfile(withAttributes = any()) } returns Profile(
                    properties = mapOf(
                        ProfileKey.ANONYMOUS_ID to ANON_ID,
                        ProfileKey.FIRST_NAME to "Kermit"
                    )
                )
            },
            apiClientMock
        )

        capturedStateObserver.captured(PROFILE_ATTRIBUTES, null)

        every { stateMock.getAsProfile(withAttributes = any()) } returns Profile(
            properties = mapOf(
                ProfileKey.ANONYMOUS_ID to "new_anon_id"
            )
        )

        capturedStateObserver.captured(null, null)

        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }

        staticClock.execute(debounceTime.toLong())

        verify(exactly = 2) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Attributes do enqueue a profile API request`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedStateObserver.captured(PROFILE_ATTRIBUTES, null)

        staticClock.execute(debounceTime.toLong())

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Push state change enqueues an API request`() {
        every { stateMock.pushState } returns "stateful"
        every { stateMock.pushToken } returns "token"

        StateSideEffects(stateMock, apiClientMock)

        capturedStateObserver.captured(ProfileKey.PUSH_STATE, null)
        verify(exactly = 1) { apiClientMock.enqueuePushToken("token", profile) }
    }

    @Test
    fun `Empty push state is ignored`() {
        every { stateMock.pushState } returns ""

        StateSideEffects(stateMock, apiClientMock)

        capturedStateObserver.captured(ProfileKey.PUSH_STATE, null)
        verify(exactly = 0) { apiClientMock.enqueuePushToken(any(), any()) }
    }

    @Test
    fun `Push token change alone does not trigger an API request`() {
        every { stateMock.pushState } returns "stateful"
        every { stateMock.pushToken } returns "token"

        StateSideEffects(stateMock, apiClientMock)

        capturedStateObserver.captured(ProfileKey.PUSH_TOKEN, null)
        verify(exactly = 0) { apiClientMock.enqueuePushToken(any(), any()) }
    }

    @Test
    fun `Reset push state on push API failure`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedApiObserver.captured(
            mockk<PushTokenApiRequest>().apply {
                every { status } returns KlaviyoApiRequest.Status.Failed
            }
        )

        assertNull(capturedPushState.captured)
    }

    @Test
    fun `Other API failures do not affect push state`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedApiObserver.captured(
            mockk<ProfileApiRequest>().apply {
                every { status } returns KlaviyoApiRequest.Status.Failed
            }
        )

        capturedApiObserver.captured(
            mockk<EventApiRequest>().apply {
                every { status } returns KlaviyoApiRequest.Status.Failed
            }
        )

        assertFalse(capturedPushState.isCaptured)
    }
}
