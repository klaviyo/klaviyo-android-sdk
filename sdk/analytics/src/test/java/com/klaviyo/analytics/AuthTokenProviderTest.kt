package com.klaviyo.analytics

import com.klaviyo.core.auth.AuthTokenProvider as CoreAuthTokenProvider
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AuthTokenProviderTest {

    private val coreCallback = mockk<CoreAuthTokenProvider.Callback>(relaxed = true)

    @Test
    fun `toCore passes a token through to the core callback`() {
        AuthTokenProvider { it.onSuccess("jwt") }.toCore().fetchToken(coreCallback)

        verify(exactly = 1) { coreCallback.onSuccess("jwt") }
        verify(exactly = 0) { coreCallback.onFailure(any()) }
    }

    @Test
    fun `toCore passes a failure through to the core callback`() {
        val error = IllegalStateException("backend down")

        AuthTokenProvider { it.onFailure(error) }.toCore().fetchToken(coreCallback)

        verify(exactly = 1) { coreCallback.onFailure(error) }
        verify(exactly = 0) { coreCallback.onSuccess(any()) }
    }
}
