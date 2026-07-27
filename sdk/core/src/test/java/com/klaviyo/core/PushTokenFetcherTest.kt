package com.klaviyo.core

import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PushTokenFetcherTest : BaseTest() {

    // Token forwarding defaults ON; production reads via Registry.config with the shared default.
    private fun setAutomaticPushTokenForwardingEnabled(enabled: Boolean) = every {
        mockConfig.getManifestBoolean(
            Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING,
            Constants.AUTOMATIC_PUSH_TOKEN_FORWARDING_DEFAULT
        )
    } returns enabled

    @Test
    fun `maybeAutoRegisterPushToken returns false when token forwarding is disabled`() {
        val mockFetcher = registerMockPushTokenFetcher()
        setAutomaticPushTokenForwardingEnabled(false)

        assertFalse(PushTokenFetcher.maybeAutoRegisterPushToken())
        verify(inverse = true) { mockFetcher.fetchAndSetPushToken(any()) }
    }

    @Test
    fun `maybeAutoRegisterPushToken returns false when no push token fetcher is registered`() {
        Registry.unregister<PushTokenFetcher>()
        setAutomaticPushTokenForwardingEnabled(true)

        assertFalse(PushTokenFetcher.maybeAutoRegisterPushToken())
    }

    @Test
    fun `maybeAutoRegisterPushToken returns false when the fetch throws synchronously`() {
        val mockFetcher = registerMockPushTokenFetcher()
        setAutomaticPushTokenForwardingEnabled(true)
        every { mockFetcher.fetchAndSetPushToken(any()) } throws RuntimeException("fetch blew up")

        assertFalse(PushTokenFetcher.maybeAutoRegisterPushToken())
    }

    @Test
    fun `maybeAutoRegisterPushToken returns true on a normal dispatch`() {
        val mockFetcher = registerMockPushTokenFetcher()
        setAutomaticPushTokenForwardingEnabled(true)

        assertTrue(PushTokenFetcher.maybeAutoRegisterPushToken())
        verify(exactly = 1) { mockFetcher.fetchAndSetPushToken(any()) }
    }
}
