package com.klaviyo.core

import com.klaviyo.core.config.AutomaticPushTokenForwarding
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PushTokenFetcherTest : BaseTest() {

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

    @Test
    fun `maybeAutoRegisterPushToken returns false when token forwarding is disabled`() {
        val mockFetcher = registerMockPushTokenFetcher()
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.DISABLED)

        assertFalse(PushTokenFetcher.maybeAutoRegisterPushToken())
        verify(inverse = true) { mockFetcher.fetchAndSetPushToken(any()) }
    }

    @Test
    fun `maybeAutoRegisterPushToken returns false when the forwarding flag is absent`() {
        val mockFetcher = registerMockPushTokenFetcher()
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.UNSET)

        assertFalse(PushTokenFetcher.maybeAutoRegisterPushToken())
        verify(inverse = true) { mockFetcher.fetchAndSetPushToken(any()) }
    }

    @Test
    fun `maybeAutoRegisterPushToken returns false when no push token fetcher is registered`() {
        Registry.unregister<PushTokenFetcher>()
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.ENABLED)

        assertFalse(PushTokenFetcher.maybeAutoRegisterPushToken())
    }

    @Test
    fun `maybeAutoRegisterPushToken returns false when the fetch throws synchronously`() {
        val mockFetcher = registerMockPushTokenFetcher()
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.ENABLED)
        every { mockFetcher.fetchAndSetPushToken(any()) } throws RuntimeException("fetch blew up")

        assertFalse(PushTokenFetcher.maybeAutoRegisterPushToken())
    }

    @Test
    fun `maybeAutoRegisterPushToken reports no dispatch instead of throwing when uninitialized`() {
        // Registry.config throws MissingConfig before Klaviyo.initialize. Contained inside the
        // method so callers get a plain "nothing was dispatched" answer and can fall back, rather
        // than each having to guard a side effect they only opted into.
        val mockFetcher = registerMockPushTokenFetcher()
        every { Registry.config } throws MissingConfig()

        assertFalse(PushTokenFetcher.maybeAutoRegisterPushToken())
        verify(inverse = true) { mockFetcher.fetchAndSetPushToken(any()) }
    }

    @Test
    fun `maybeAutoRegisterPushToken returns true on a normal dispatch`() {
        val mockFetcher = registerMockPushTokenFetcher()
        setAutomaticPushTokenForwarding(AutomaticPushTokenForwarding.ENABLED)

        assertTrue(PushTokenFetcher.maybeAutoRegisterPushToken())
        verify(exactly = 1) { mockFetcher.fetchAndSetPushToken(any()) }
    }
}
