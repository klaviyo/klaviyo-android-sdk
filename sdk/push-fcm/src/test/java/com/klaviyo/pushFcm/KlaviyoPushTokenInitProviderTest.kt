package com.klaviyo.pushFcm

import com.klaviyo.core.PushTokenFetcher
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KlaviyoPushTokenInitProviderTest : BaseTest() {

    @After
    override fun cleanup() {
        super.cleanup()
        Registry.unregister<PushTokenFetcher>()
    }

    @Test
    fun `onCreate registers the FCM-backed PushTokenFetcher`() {
        Registry.unregister<PushTokenFetcher>()
        assertNull(Registry.getOrNull<PushTokenFetcher>())

        val created = KlaviyoPushTokenInitProvider().onCreate()

        assertTrue(created)
        assertTrue(Registry.getOrNull<PushTokenFetcher>() is KlaviyoPushTokenFetcher)
    }
}
