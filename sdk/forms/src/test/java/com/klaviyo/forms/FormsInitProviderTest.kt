package com.klaviyo.forms

import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FormsInitProviderTest : BaseTest() {
    @After
    override fun cleanup() {
        Registry.unregister<FormsProvider>()
        super.cleanup()
    }

    @Test
    fun `onCreate registers FormsProvider in Registry`() {
        FormsInitProvider().onCreate()
        assertNotNull(Registry.getOrNull<FormsProvider>())
    }
}
