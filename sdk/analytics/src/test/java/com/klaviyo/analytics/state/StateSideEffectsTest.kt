package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.StateKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.ApiObserver
import com.klaviyo.analytics.networking.requests.EventApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest
import com.klaviyo.analytics.networking.requests.KlaviyoError
import com.klaviyo.analytics.networking.requests.KlaviyoErrorResponse
import com.klaviyo.analytics.networking.requests.KlaviyoErrorSource
import com.klaviyo.analytics.networking.requests.ProfileApiRequest
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Constants
import com.klaviyo.core.PushTokenFetcher
import com.klaviyo.core.Registry
import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.core.config.AutomaticPushTokenForwarding
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.fixtures.BaseTest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StateSideEffectsTest : BaseTest() {

    private val profile = Profile(email = EMAIL)
    private val capturedProfile = slot<Profile>()
    private val capturedApiObserver = slot<ApiObserver>()
    private val capturedStateChangeObserver = slot<StateChangeObserver>()
    private val capturedPushState = slot<String?>()
    private val apiClientMock: ApiClient = mockk<ApiClient>().apply {
        every { onApiRequest(any(), capture(capturedApiObserver)) } returns Unit
        every { offApiRequest(any()) } returns Unit
        every { enqueueProfile(capture(capturedProfile)) } returns mockk(relaxed = true)
        every { enqueueEvent(any(), any()) } returns mockk(relaxed = true)
        every { enqueuePushToken(any(), any()) } returns mockk(relaxed = true)
    }

    private val stateMock = mockk<State>().apply {
        every { onStateChange(capture(capturedStateChangeObserver)) } returns Unit
        every { offStateChange(any<StateChangeObserver>()) } returns Unit
        every { pushState = captureNullable(capturedPushState) } returns Unit
        every { getAsProfile(withAttributes = any()) } returns profile
        every { resetAttributes() } returns Unit
        every { pushToken } returns null
    }

    private val authTokenManagerMock = mockk<AuthTokenManager>().apply {
        every { invalidate() } returns AUTH_GENERATION
        coEvery { clearTokenState(any()) } returns Unit
    }

    private val klaviyoStateMock = mockk<KlaviyoState>().apply {
        every { onStateChange(capture(capturedStateChangeObserver)) } returns Unit
        every { resetPhoneNumber() } returns Unit
        every { resetEmail() } returns Unit
    }

    @Before
    override fun setup() {
        super.setup()
        Registry.register<ApiClient>(apiClientMock)
        Registry.register<AuthTokenManager>(authTokenManagerMock)
    }

    @After
    override fun cleanup() {
        Registry.unregister<ApiClient>()
        Registry.unregister<AuthTokenManager>()
        Registry.unregister<PushTokenFetcher>()
        super.cleanup()
    }

    @Test
    fun `Subscribes on init and detach unsubscribes`() {
        val sideEffects = StateSideEffects(stateMock, apiClientMock)
        verify { stateMock.onStateChange(any<StateChangeObserver>()) }
        verify { apiClientMock.onApiRequest(any(), any()) }
        verify { mockLifecycleMonitor.onActivityEvent(any()) }

        sideEffects.detach()
        verify { stateMock.offStateChange(any<StateChangeObserver>()) }
        verify { apiClientMock.offApiRequest(any()) }
        verify { mockLifecycleMonitor.offActivityEvent(any()) }
    }

    @Test
    fun `Profile changes enqueue a single profile API request`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.ProfileIdentifier(ProfileKey.EMAIL, null))
        capturedStateChangeObserver.captured(StateChange.ProfileAttributes(mockk()))
        capturedStateChangeObserver.captured(StateChange.ProfileReset(mockk()))

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
    fun `Pending profile merge does not restore a removed identifier`() {
        var current = Profile(externalId = EXTERNAL_ID, email = EMAIL).apply {
            setProperty(ProfileKey.FIRST_NAME, "Kermit")
        }
        every { stateMock.getAsProfile(withAttributes = any()) } answers { current }
        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.ProfileIdentifier(ProfileKey.EMAIL, null))
        current = Profile(externalId = EXTERNAL_ID).apply {
            setProperty(ProfileKey.LAST_NAME, "Frog")
        }
        capturedStateChangeObserver.captured(StateChange.ProfileIdentifier(ProfileKey.EMAIL, EMAIL))
        staticClock.execute(debounceTime.toLong())

        verify(exactly = 1) {
            apiClientMock.enqueueProfile(
                match {
                    it.externalId == EXTERNAL_ID &&
                        it.email == null &&
                        it[ProfileKey.FIRST_NAME] == "Kermit" &&
                        it[ProfileKey.LAST_NAME] == "Frog"
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

        capturedStateChangeObserver.captured(StateChange.ProfileAttributes(mockk()))

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

        capturedStateChangeObserver.captured(StateChange.ProfileAttributes(mockk()))

        every { stateMock.getAsProfile(withAttributes = any()) } returns Profile(
            properties = mapOf(
                ProfileKey.ANONYMOUS_ID to "new_anon_id"
            )
        )

        capturedStateChangeObserver.captured(StateChange.ProfileReset(mockk()))

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

        capturedStateChangeObserver.captured(StateChange.ProfileAttributes(mockk()))

        every { stateMock.getAsProfile(withAttributes = any()) } returns Profile(
            properties = mapOf(
                ProfileKey.ANONYMOUS_ID to "new_anon_id"
            )
        )

        capturedStateChangeObserver.captured(StateChange.ProfileReset(mockk()))

        verify(exactly = 1) { apiClientMock.enqueuePushToken(PUSH_TOKEN, any()) }
    }

    @Test
    fun `Profile reset invalidates auth token state then queues generation-matched clear`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.ProfileReset(mockk()))
        dispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            authTokenManagerMock.invalidate()
            authTokenManagerMock.clearTokenState(expectedGeneration = AUTH_GENERATION)
        }
        verify(exactly = 1) { authTokenManagerMock.invalidate() }
        coVerify(exactly = 1) { authTokenManagerMock.clearTokenState(any()) }
    }

    @Test
    fun `Profile reset without a registered AuthTokenManager still enqueues the profile`() {
        Registry.unregister<AuthTokenManager>()
        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.ProfileReset(mockk()))
        staticClock.execute(debounceTime.toLong())

        verify(exactly = 1) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Attributes do enqueue a profile API request`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.ProfileAttributes(mockk()))

        staticClock.execute(debounceTime.toLong())

        verify(exactly = 0) { apiClientMock.enqueueProfile(any()) }
    }

    @Test
    fun `Push state change enqueues an API request`() {
        every { stateMock.pushState } returns "stateful"
        every { stateMock.pushToken } returns "token"

        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.KeyValue(StateKey.PUSH_STATE, null))
        verify(exactly = 1) { apiClientMock.enqueuePushToken("token", profile) }
    }

    @Test
    fun `Empty push state is ignored`() {
        every { stateMock.pushState } returns ""

        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.KeyValue(StateKey.PUSH_STATE, null))
        verify(exactly = 0) { apiClientMock.enqueuePushToken(any(), any()) }
    }

    @Test
    fun `Push token change alone does not trigger an API request`() {
        every { stateMock.pushState } returns "stateful"
        every { stateMock.pushToken } returns "token"

        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.KeyValue(ProfileKey.PUSH_TOKEN, null))
        verify(exactly = 0) { apiClientMock.enqueuePushToken(any(), any()) }
    }

    @Test
    fun `API key change invalidates auth token state then queues generation-matched clear`() {
        every { stateMock.pushState } returns null
        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(StateChange.ApiKey(API_KEY))
        dispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            authTokenManagerMock.invalidate()
            authTokenManagerMock.clearTokenState(expectedGeneration = AUTH_GENERATION)
        }
    }

    @Test
    fun `Profile identifier changes invalidate and clear auth token state`() {
        every { stateMock.getAsProfile(withAttributes = any()) } returns
            Profile(externalId = EXTERNAL_ID, email = EMAIL, phoneNumber = PHONE)
        StateSideEffects(stateMock, apiClientMock)

        listOf(
            StateChange.ProfileIdentifier(ProfileKey.EXTERNAL_ID, OTHER_EXTERNAL_ID),
            StateChange.ProfileIdentifier(ProfileKey.EMAIL, OTHER_EMAIL),
            StateChange.ProfileIdentifier(ProfileKey.PHONE_NUMBER, OTHER_PHONE)
        ).forEach { change ->
            clearMocks(authTokenManagerMock, answers = false)

            capturedStateChangeObserver.captured(change)

            try {
                verifyTokenStateReset(times = 1)
            } catch (e: AssertionError) {
                throw AssertionError(change.toString(), e)
            }
        }
    }

    @Test
    fun `Individual identifier setters reset auth token state only on replacement`() {
        listOf(
            TokenResetCase("anonymous to email", true) { email = EMAIL },
            TokenResetCase("anonymous to phone", true) { phoneNumber = PHONE },
            TokenResetCase("anonymous to external ID", true) { externalId = EXTERNAL_ID },
            TokenResetCase("add external ID", false, { email = EMAIL }) { externalId = EXTERNAL_ID },
            TokenResetCase("add phone", false, { email = EMAIL }) { phoneNumber = PHONE },
            TokenResetCase("add email", false, { externalId = EXTERNAL_ID }) { email = EMAIL },
            TokenResetCase("change email", true, { email = EMAIL }) { email = OTHER_EMAIL },
            TokenResetCase(
                "change phone",
                true,
                { setProfile(Profile(email = EMAIL, phoneNumber = PHONE)) }
            ) {
                phoneNumber = OTHER_PHONE
            },
            TokenResetCase("change external ID", true, { setProfile(Profile(EXTERNAL_ID, EMAIL)) }) {
                externalId = OTHER_EXTERNAL_ID
            },
            TokenResetCase("case-only email change", true, { email = EMAIL }) {
                email = EMAIL.uppercase()
            },
            TokenResetCase("same email", false, { email = EMAIL }) { email = EMAIL },
            TokenResetCase("reset profile", true, { email = EMAIL }) { reset() }
        ).forEach(::verifyTokenResetCase)
    }

    @Test
    fun `setProfile resets auth token state only on replacement`() {
        listOf(
            TokenResetCase("anonymous to identified", true) {
                setProfile(Profile(externalId = EXTERNAL_ID, email = EMAIL))
            },
            TokenResetCase("compatible addition", false, { email = EMAIL }) {
                setProfile(Profile(externalId = EXTERNAL_ID, email = EMAIL, phoneNumber = PHONE))
            },
            TokenResetCase("compatible removal", false, { setProfile(Profile(EXTERNAL_ID, EMAIL)) }) {
                setProfile(Profile(email = EMAIL))
            },
            TokenResetCase("compatible swap", false, { setProfile(Profile(EXTERNAL_ID, EMAIL)) }) {
                setProfile(Profile(email = EMAIL, phoneNumber = PHONE))
            },
            TokenResetCase("unchanged", false, { setProfile(Profile(EXTERNAL_ID, EMAIL)) }) {
                setProfile(Profile(EXTERNAL_ID, EMAIL))
            },
            TokenResetCase("conflicting email", true, { setProfile(Profile(EXTERNAL_ID, EMAIL)) }) {
                setProfile(Profile(EXTERNAL_ID, OTHER_EMAIL))
            },
            TokenResetCase("no shared identifier", true, { email = EMAIL }) {
                setProfile(Profile(phoneNumber = PHONE))
            },
            TokenResetCase("identified to anonymous", true, { email = EMAIL }) {
                setProfile(Profile())
            }
        ).forEach(::verifyTokenResetCase)
    }

    @Test
    fun `Resetting a rejected identifier resets auth token state only when none remain`() {
        listOf(
            TokenResetCase("email with phone remaining", false, {
                setProfile(Profile(email = EMAIL, phoneNumber = PHONE))
            }) { resetEmail() },
            TokenResetCase("phone with external ID remaining", false, {
                setProfile(Profile(externalId = EXTERNAL_ID, phoneNumber = PHONE))
            }) { resetPhoneNumber() },
            TokenResetCase("only email", true, { email = EMAIL }) { resetEmail() },
            TokenResetCase("only phone", true, { phoneNumber = PHONE }) { resetPhoneNumber() },
            TokenResetCase("unset email", false, { phoneNumber = PHONE }) { resetEmail() }
        ).forEach(::verifyTokenResetCase)
    }

    @Test
    fun `Invalid email response enqueues one profile update without the email`() {
        val state = KlaviyoState()
        Registry.register<State>(state)
        StateSideEffects(state, apiClientMock)
        state.setProfile(Profile(email = EMAIL, phoneNumber = PHONE))
        staticClock.execute(debounceTime.toLong())
        clearMocks(apiClientMock, authTokenManagerMock, answers = false)

        repeat(2) {
            capturedApiObserver.captured(invalidInputRequest("/data/attributes/email"))
            staticClock.execute(debounceTime.toLong())
        }

        assertNull(state.email)
        verify(exactly = 1) {
            apiClientMock.enqueueProfile(match { it.email == null && it.phoneNumber == PHONE })
        }
        verify(exactly = 0) { authTokenManagerMock.invalidate() }
        assertTrue(staticClock.scheduledTasks.isEmpty())
        Registry.unregister<State>()
    }

    @Test
    fun `Invalid identifiers are removed one at a time until none remain`() {
        val state = KlaviyoState()
        Registry.register<State>(state)
        StateSideEffects(state, apiClientMock)
        state.setProfile(Profile(email = EMAIL, phoneNumber = PHONE))
        staticClock.execute(debounceTime.toLong())
        clearMocks(apiClientMock, authTokenManagerMock, answers = false)

        listOf("/data/attributes/email", "/data/attributes/phone_number").forEach { pointer ->
            repeat(2) {
                capturedApiObserver.captured(invalidInputRequest(pointer))
                staticClock.execute(debounceTime.toLong())
            }
        }

        verify(exactly = 2) { apiClientMock.enqueueProfile(any()) }
        verify(exactly = 1) {
            apiClientMock.enqueueProfile(match { it.email == null && it.phoneNumber == null })
        }
        verify(exactly = 1) { authTokenManagerMock.invalidate() }
        assertTrue(staticClock.scheduledTasks.isEmpty())
        Registry.unregister<State>()
    }

    @Test
    fun `Anonymous ID and attribute changes do not touch auth token state`() {
        StateSideEffects(stateMock, apiClientMock)

        capturedStateChangeObserver.captured(
            StateChange.ProfileIdentifier(ProfileKey.ANONYMOUS_ID, null)
        )
        capturedStateChangeObserver.captured(StateChange.ProfileAttributes(mockk()))
        staticClock.execute(debounceTime.toLong())

        verifyTokenStateReset(times = 0)
    }

    @Test
    fun `Later state observers see the auth token already invalidated`() {
        var invalidated = false
        every { authTokenManagerMock.invalidate() } answers {
            invalidated = true
            AUTH_GENERATION
        }
        every { authTokenManagerMock.isCurrentToken(OLD_TOKEN) } answers { !invalidated }
        val state = KlaviyoState()
        StateSideEffects(state, apiClientMock)
        val observedIsCurrent = mutableListOf<Boolean>()
        state.onStateChange { change ->
            if (change.key != ProfileKey.ANONYMOUS_ID) {
                observedIsCurrent += authTokenManagerMock.isCurrentToken(OLD_TOKEN)
            }
        }

        listOf({ state.email = EMAIL }, { state.reset() }).forEach { act ->
            invalidated = false
            act()
        }

        assertEquals(listOf(false, false), observedIsCurrent)
    }

    @Test
    fun `Later state observers see the auth token invalidated only on replacement`() {
        var invalidated = false
        every { authTokenManagerMock.invalidate() } answers {
            invalidated = true
            AUTH_GENERATION
        }
        every { authTokenManagerMock.isCurrentToken(OLD_TOKEN) } answers { !invalidated }
        val state = KlaviyoState()
        StateSideEffects(state, apiClientMock)
        val observedIsCurrent = mutableListOf<Boolean>()
        state.onStateChange { change ->
            if (change.key != ProfileKey.ANONYMOUS_ID) {
                observedIsCurrent += authTokenManagerMock.isCurrentToken(OLD_TOKEN)
            }
        }

        listOf(
            { state.email = EMAIL },
            { state.externalId = EXTERNAL_ID },
            { state.email = OTHER_EMAIL },
            { state.reset() }
        ).forEach { act ->
            invalidated = false
            act()
        }

        assertEquals(listOf(false, true, false, false), observedIsCurrent)
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

    @Test
    fun `updated phone error source pointer still resets state`() {
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
                                pointer = "/data/attributes/profile/data/attributes/phone_number"
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
    fun `updated email error source pointer still resets state`() {
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
                            detail = "This email is complete chicanery",
                            source = KlaviyoErrorSource(
                                pointer = "/data/attributes/profile/data/attributes/email"
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
    fun `Resumed lifecycle event triggers a refresh with no prior Started event`() {
        // No fetcher registered: the only path available is a direct refresh from the stored token.
        // Fired directly, with no intervening Started event, matching a dismissed system dialog.
        fireResumedEvent()

        verify(exactly = 1) { stateMock.refreshPushState() }
    }

    @Test
    fun `Resumed lifecycle event re-fetches push token when automatic forwarding is enabled`() {
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.ENABLED)
        val mockFetcher = registerMockPushTokenFetcher()

        fireResumedEvent()

        // Fetch and refresh are mutually exclusive: dispatching a fetch must suppress the refresh fallback.
        verify(exactly = 1) { mockFetcher.fetchAndSetPushToken(any()) }
        verify(exactly = 0) { stateMock.refreshPushState() }
    }

    @Test
    fun `Resumed lifecycle event does not re-fetch push token when automatic forwarding is disabled`() {
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.DISABLED)
        val mockFetcher = registerMockPushTokenFetcher()

        fireResumedEvent()

        verify(inverse = true) { mockFetcher.fetchAndSetPushToken(any()) }
        verify(exactly = 1) { stateMock.refreshPushState() }
    }

    @Test
    fun `Resumed lifecycle event refreshes from the stored token when the forwarding flag is absent`() {
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.UNSET)
        val mockFetcher = registerMockPushTokenFetcher()

        fireResumedEvent()

        // Matches the pre-flag resume behavior: recompute push state from the token already in state.
        verify(inverse = true) { mockFetcher.fetchAndSetPushToken(any()) }
        verify(exactly = 1) { stateMock.refreshPushState() }
    }

    @Test
    fun `Resumed lifecycle event falls back to refreshPushState when the fetcher reports unavailable`() {
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.ENABLED)
        val mockFetcher = registerMockPushTokenFetcher()
        every { mockFetcher.fetchAndSetPushToken(any()) } answers {
            firstArg<() -> Unit>().invoke()
        }

        fireResumedEvent()

        // The dispatch itself succeeded (didn't throw) AND onUnavailable fired synchronously —
        // refreshPushState must still run exactly once, not be skipped or double-invoked.
        verify(exactly = 1) { mockFetcher.fetchAndSetPushToken(any()) }
        verify(exactly = 1) { stateMock.refreshPushState() }
    }

    @Test
    fun `FirstStarted lifecycle event does not trigger a push state refresh`() {
        // FirstStarted alone (no Resumed) must not trigger either path.
        val mockFetcher = registerMockPushTokenFetcher()

        fireLifecycleEvent(ActivityEvent.FirstStarted(mockk()))

        verify(inverse = true) { stateMock.refreshPushState() }
        verify(inverse = true) { mockFetcher.fetchAndSetPushToken(any()) }
    }

    // The proactive fetch requires an explicit opt-in; production resolves the three-valued flag via
    // Registry.config. BaseTest leaves hasManifestKey false, i.e. UNSET, unless a test says otherwise.
    private fun setAutomaticPushTokenForwarding(state: AutomaticPushTokenForwarding) {
        every {
            mockConfig.hasManifestKey(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING)
        } returns (state != AutomaticPushTokenForwarding.UNSET)
        every {
            mockConfig.getManifestBoolean(Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING, false)
        } returns (state == AutomaticPushTokenForwarding.ENABLED)
    }

    // Registers stateMock, captures the lifecycle observer via a new StateSideEffects, and fires Resumed
    private fun fireResumedEvent() = fireLifecycleEvent(ActivityEvent.Resumed(mockk()))

    // Registers stateMock, captures the lifecycle observer via a new StateSideEffects, and fires the given event
    private fun fireLifecycleEvent(event: ActivityEvent) {
        Registry.register<State>(stateMock)
        every { stateMock.pushToken } returns "mocked_push_token"
        every { stateMock.pushToken = any() } returns Unit
        every { stateMock.refreshPushState() } returns Unit
        val capturedLifecycleObserver = slot<ActivityObserver>()
        every { mockLifecycleMonitor.onActivityEvent(capture(capturedLifecycleObserver)) } returns Unit

        StateSideEffects(
            state = stateMock,
            apiClient = apiClientMock,
            lifecycleMonitor = mockLifecycleMonitor
        )

        capturedLifecycleObserver.captured(event)
    }

    private fun invalidInputRequest(pointer: String): ProfileApiRequest =
        mockk<ProfileApiRequest>().apply {
            every { status } returns KlaviyoApiRequest.Status.Failed
            every { responseCode } returns 400
            every { errorBody } returns KlaviyoErrorResponse(
                listOf(
                    KlaviyoError(
                        id = "invalid-input",
                        status = 400,
                        title = KlaviyoErrorResponse.INVALID_INPUT_TITLE,
                        detail = "Invalid input",
                        source = KlaviyoErrorSource(pointer = pointer)
                    )
                )
            )
        }

    private class TokenResetCase(
        val description: String,
        val expectReset: Boolean,
        val arrange: KlaviyoState.() -> Unit = {},
        val act: KlaviyoState.() -> Unit
    )

    /**
     * Arrange a real [KlaviyoState] observed by [StateSideEffects], then verify whether [act]
     * resets auth token state.
     */
    private fun verifyTokenResetCase(case: TokenResetCase) {
        val state = KlaviyoState()
        StateSideEffects(state, apiClientMock)
        case.arrange(state)
        dispatcher.scheduler.advanceUntilIdle()
        clearMocks(authTokenManagerMock, answers = false)

        case.act(state)

        try {
            verifyTokenStateReset(times = if (case.expectReset) 1 else 0)
        } catch (e: AssertionError) {
            throw AssertionError(case.description, e)
        } finally {
            state.reset()
            dispatcher.scheduler.advanceUntilIdle()
        }
    }

    private fun verifyTokenStateReset(times: Int) {
        verify(exactly = times) { authTokenManagerMock.invalidate() }

        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = times) { authTokenManagerMock.clearTokenState(any()) }
        coVerify(exactly = times) {
            authTokenManagerMock.clearTokenState(expectedGeneration = AUTH_GENERATION)
        }
    }

    private companion object {
        const val AUTH_GENERATION = 7L
        const val OLD_TOKEN = "old.jwt.token"
        const val OTHER_EMAIL = "other@domain.com"
        const val OTHER_PHONE = "+15556667777"
        const val OTHER_EXTERNAL_ID = "hijklmn"
    }
}
