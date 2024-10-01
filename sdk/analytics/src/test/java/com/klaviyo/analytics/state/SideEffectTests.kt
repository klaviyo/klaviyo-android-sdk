package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.PROFILE_ATTRIBUTES
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.ApiObserver
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoError
import com.klaviyo.analytics.networking.requests.KlaviyoErrorResponse
import com.klaviyo.analytics.networking.requests.KlaviyoErrorSource
import com.klaviyo.analytics.networking.requests.ProfileApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SideEffectTests : BaseTest() {

    private val profile = Profile(email = EMAIL)
    private val capturedProfile = slot<Profile>()
    private val capturedApiObserver = slot<ApiObserver>()
    private val capturedStateObserver = slot<StateObserver>()
    private val capturedPushState = slot<String?>()
    private val apiClientMock: ApiClient = mockk<ApiClient>().apply {
        every { onApiRequest(any(), capture(capturedApiObserver)) } returns Unit
        every { offApiRequest(any()) } returns Unit
        every { enqueueProfile(capture(capturedProfile)) } returns Unit
        every { enqueueEvent(any(), any()) } returns Unit
        every { enqueuePushToken(any(), any()) } returns Unit
    }

    private val stateMock = mockk<State>().apply {
        every { onStateChange(capture(capturedStateObserver)) } returns Unit
        every { offStateChange(any()) } returns Unit
        every { pushState = captureNullable(capturedPushState) } returns Unit
        every { getAsProfile(withAttributes = any()) } returns profile
        every { resetAttributes() } returns Unit
        every { pushToken } returns null
    }

    private val klaviyoStateMock = mockk<KlaviyoState>().apply {
        every { onStateChange(capture(capturedStateObserver)) } returns Unit
        every { resetPhoneNumber() } returns Unit
        every { resetEmail() } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<ApiClient>(apiClientMock)
    }

    @After
    override fun cleanup() {
        Registry.unregister<ApiClient>()
        super.cleanup()
    }

    @Test
    fun `Subscribes on init and unsubscribes`() {
        val sideEffects = StateSideEffects(stateMock, apiClientMock)
        verify { stateMock.onStateChange(any()) }
        verify { apiClientMock.onApiRequest(any(), any()) }
        verify { mockLifecycleMonitor.onActivityEvent(any()) }

        sideEffects.detach()
        verify { stateMock.offStateChange(any()) }
        verify { apiClientMock.offApiRequest(any()) }
        verify { mockLifecycleMonitor.offActivityEvent(any()) }
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
    fun `Resetting profile enqueues Profiles API call immediately`() {
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
    fun `Resetting profile enqueues Push Token API call immediately when push token is in state`() {
        every { stateMock.pushToken } returns PUSH_TOKEN

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

        verify(exactly = 1) { apiClientMock.enqueuePushToken(PUSH_TOKEN, any()) }
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
                every { responseCode } returns 412
            }
        )

        assertNull(capturedPushState.captured)
    }

    @Test
    fun `Invalid input on phone number resets field`() {
        Registry.register<State>(klaviyoStateMock)
        StateSideEffects(
            state = klaviyoStateMock,
            apiClient = apiClientMock
        )

        capturedApiObserver.captured(
            mockk<ProfileApiRequest>().apply {
                every { status } returns KlaviyoApiRequest.Status.Failed
                every { responseCode } returns 400
                every { errorBody } returns KlaviyoErrorResponse(
                    listOf(
                        KlaviyoError(
                            id = "67ed6dbf-1653-499b-a11d-30310aa01ff7",
                            status = 400,
                            title = "Invalid input.",
                            detail = "Invalid phone number format (Example of a valid format: +12345678901)",
                            source = KlaviyoErrorSource(
                                pointer = "/data/attributes/phone_number"
                            )
                        )
                    )
                )
            }
        )

        verify { klaviyoStateMock.resetPhoneNumber() }
        Registry.unregister<State>()
    }

    @Test
    fun `Invalid input on email resets field`() {
        Registry.register<State>(klaviyoStateMock)
        StateSideEffects(
            state = klaviyoStateMock,
            apiClient = apiClientMock
        )

        capturedApiObserver.captured(
            mockk<ProfileApiRequest>().apply {
                every { status } returns KlaviyoApiRequest.Status.Failed
                every { responseCode } returns 400
                every { errorBody } returns KlaviyoErrorResponse(
                    listOf(
                        KlaviyoError(
                            id = "4f739784-390b-4df3-acd8-6eb07d60e6b4",
                            status = 400,
                            title = "Invalid input.",
                            detail = "Invalid email address",
                            source = KlaviyoErrorSource(
                                pointer = "/data/attributes/email"
                            )
                        )
                    )
                )
            }
        )

        verify { klaviyoStateMock.resetEmail() }
        Registry.unregister<State>()
    }

    @Test
    fun `Empty error body does not reset fields`() {
        Registry.register<State>(klaviyoStateMock)
        StateSideEffects(
            state = klaviyoStateMock,
            apiClient = apiClientMock
        )

        capturedApiObserver.captured(
            mockk<ProfileApiRequest>().apply {
                every { status } returns KlaviyoApiRequest.Status.Failed
                every { responseCode } returns 400
                every { errorBody } returns KlaviyoErrorResponse(
                    listOf()
                )
            }
        )

        verify(exactly = 0) { klaviyoStateMock.resetEmail() }
        verify(exactly = 0) { klaviyoStateMock.resetEmail() }
        Registry.unregister<State>()
    }

    @Test
    fun `Other API failures do not affect push state`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedApiObserver.captured(
            mockk<ProfileApiRequest>().apply {
                every { status } returns KlaviyoApiRequest.Status.Failed
                every { responseCode } returns 412
            }
        )

        capturedApiObserver.captured(
            mockk<EventApiRequest>().apply {
                every { status } returns KlaviyoApiRequest.Status.Failed
                every { responseCode } returns 412
            }
        )

        assertFalse(capturedPushState.isCaptured)
    }
}
