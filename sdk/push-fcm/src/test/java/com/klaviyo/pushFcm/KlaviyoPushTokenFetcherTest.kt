package com.klaviyo.pushFcm

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KlaviyoPushTokenFetcherTest : BaseTest() {
    private val stubToken = "fcm_stub_token"
    private val mockTask = mockk<Task<String>>()
    private val mockFirebaseMessaging = mockk<FirebaseMessaging>().apply {
        every { token } returns mockTask
    }

    @Before
    override fun setup() {
        super.setup()
        // mockkStatic is required for @JvmStatic methods on the Klaviyo facade
        mockkStatic(Klaviyo::class)
        mockkObject(Klaviyo)
        every { Klaviyo.setPushToken(any()) } returns Klaviyo

        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns mockFirebaseMessaging
    }

    @After
    override fun cleanup() {
        super.cleanup()
        unmockkObject(Klaviyo)
        unmockkStatic(Klaviyo::class)
        unmockkStatic(FirebaseMessaging::class)
    }

    @Test
    fun `fetchAndSetPushToken forwards the fetched token to Klaviyo on success`() {
        val successSlot = slot<OnSuccessListener<String>>()
        every { mockTask.addOnSuccessListener(capture(successSlot)) } answers {
            successSlot.captured.onSuccess(stubToken)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        KlaviyoPushTokenFetcher().fetchAndSetPushToken()

        verify(exactly = 1) { Klaviyo.setPushToken(stubToken) }
    }

    @Test
    fun `fetchAndSetPushToken does not invoke onUnavailable on success`() {
        val successSlot = slot<OnSuccessListener<String>>()
        every { mockTask.addOnSuccessListener(capture(successSlot)) } answers {
            successSlot.captured.onSuccess(stubToken)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask
        var onUnavailableInvoked = false

        KlaviyoPushTokenFetcher().fetchAndSetPushToken { onUnavailableInvoked = true }

        assertFalse(onUnavailableInvoked)
    }

    @Test
    fun `fetchAndSetPushToken contains a throwing onUnavailable on the async failure path`() {
        // The failure listener fires after fetchAndSetPushToken has returned, so no guard on the
        // caller's stack can catch a throwing callback — it must be contained here or it lands on
        // whatever thread the provider completed on.
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        val failureSlot = slot<OnFailureListener>()
        every { mockTask.addOnFailureListener(capture(failureSlot)) } answers {
            failureSlot.captured.onFailure(RuntimeException("fetch failed"))
            mockTask
        }

        KlaviyoPushTokenFetcher().fetchAndSetPushToken { throw RuntimeException("callback blew up") }

        verify { spyLog.error(any(), any()) }
    }

    @Test
    fun `fetchAndSetPushToken contains a throwing onUnavailable when FirebaseMessaging is unavailable`() {
        every { FirebaseMessaging.getInstance() } throws IllegalStateException("no FirebaseApp")

        KlaviyoPushTokenFetcher().fetchAndSetPushToken { throw RuntimeException("callback blew up") }

        verify { spyLog.error(any(), any()) }
    }

    @Test
    fun `fetchAndSetPushToken logs a warning and does not crash when the fetch fails`() {
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        val failureSlot = slot<OnFailureListener>()
        every { mockTask.addOnFailureListener(capture(failureSlot)) } answers {
            failureSlot.captured.onFailure(RuntimeException("fetch failed"))
            mockTask
        }

        KlaviyoPushTokenFetcher().fetchAndSetPushToken()

        verify(inverse = true) { Klaviyo.setPushToken(any()) }
        verify { spyLog.warning(any(), any()) }
    }

    @Test
    fun `fetchAndSetPushToken invokes onUnavailable when the fetch fails`() {
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        val failureSlot = slot<OnFailureListener>()
        every { mockTask.addOnFailureListener(capture(failureSlot)) } answers {
            failureSlot.captured.onFailure(RuntimeException("fetch failed"))
            mockTask
        }
        var onUnavailableInvoked = false

        KlaviyoPushTokenFetcher().fetchAndSetPushToken { onUnavailableInvoked = true }

        assertTrue(onUnavailableInvoked)
    }

    @Test
    fun `fetchAndSetPushToken survives when FirebaseMessaging is unavailable`() {
        every {
            FirebaseMessaging.getInstance()
        } throws IllegalStateException("Firebase is not configured")

        // Should swallow the synchronous failure rather than crash initialize
        KlaviyoPushTokenFetcher().fetchAndSetPushToken()

        verify(inverse = true) { Klaviyo.setPushToken(any()) }
        verify { spyLog.warning(any(), any()) }
    }

    @Test
    fun `fetchAndSetPushToken swallows unexpected synchronous failures`() {
        every {
            FirebaseMessaging.getInstance()
        } throws RuntimeException("unexpected Firebase failure")

        KlaviyoPushTokenFetcher().fetchAndSetPushToken()

        verify(inverse = true) { Klaviyo.setPushToken(any()) }
        verify { spyLog.warning(any(), any()) }
    }

    @Test
    fun `fetchAndSetPushToken invokes onUnavailable when FirebaseMessaging is unavailable`() {
        every {
            FirebaseMessaging.getInstance()
        } throws IllegalStateException("Firebase is not configured")
        var onUnavailableInvoked = false

        KlaviyoPushTokenFetcher().fetchAndSetPushToken { onUnavailableInvoked = true }

        assertTrue(onUnavailableInvoked)
    }
}
