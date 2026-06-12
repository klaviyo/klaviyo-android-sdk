package com.klaviyo.core

import com.klaviyo.core.auth.AuthTokenManager
import com.klaviyo.fixtures.BaseTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CoreInitProviderTest : BaseTest() {
    @After
    override fun cleanup() {
        Registry.unregister<AuthTokenManager>()
        super.cleanup()
    }

    @Test
    fun `onCreate registers AuthTokenManager in Registry`() {
        CoreInitProvider().onCreate()
        assertNotNull(Registry.getOrNull<AuthTokenManager>())
    }
}
