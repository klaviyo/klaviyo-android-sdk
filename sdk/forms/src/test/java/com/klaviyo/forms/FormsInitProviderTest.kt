package com.klaviyo.forms

import com.klaviyo.core.Registry
import com.klaviyo.core.forms.FormsPreviewTokenSink
import com.klaviyo.fixtures.BaseTest
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class FormsInitProviderTest : BaseTest() {
    @After
    override fun cleanup() {
        Registry.unregister<FormsProvider>()
        Registry.unregister<FormsPreviewTokenSink>()
        super.cleanup()
    }

    @Test
    fun `onCreate registers FormsProvider in Registry`() {
        FormsInitProvider().onCreate()
        assertNotNull(Registry.getOrNull<FormsProvider>())
    }

    @Test
    fun `onCreate registers a FormsPreviewTokenSink that forwards to the provider`() {
        val provider = mockk<FormsProvider>(relaxed = true)
        Registry.register<FormsProvider>(provider)
        FormsInitProvider().onCreate()

        Registry.get<FormsPreviewTokenSink>().startPreview("token")

        verify { provider.startPreview("token") }
    }
}
