package com.klaviyo.forms.bridge

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry
import com.klaviyo.forms.reInitializeInAppForms
import com.klaviyo.forms.webview.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CompanyObserverTest {
    private val observerSlot = slot<StateChangeObserver>()
    private val stateMock = mockk<State>(relaxed = true).apply {
        every { onStateChange(capture(observerSlot)) } returns Unit
        every { apiKey } returns "new_key"
    }

    private val mockWebViewClient = mockk<WebViewClient>(relaxed = true).apply {
        every { destroyWebView() } returns this
    }

    @Before
    fun setup() {
        mockkStatic("com.klaviyo.forms.InAppFormsKt") // Mock the extension function
        every { Klaviyo.reInitializeInAppForms() } returns Klaviyo
        Registry.register<WebViewClient>(mockWebViewClient)
        Registry.register<State>(stateMock)
    }

    @After
    fun cleanup() {
        Registry.unregister<WebViewClient>()
        Registry.unregister<State>()
        unmockkAll()
    }

    @Test
    fun `handshake is correct`() = assertEquals(
        null,
        CompanyObserver().handshake
    )

    @Test
    fun `startObserver attaches to state change`() {
        CompanyObserver().startObserver()
        assert(observerSlot.isCaptured)
        observerSlot.captured.invoke(StateChange.ApiKey("new_key"))
        verify { Klaviyo.reInitializeInAppForms() }
    }

    @Test
    fun `observer ignores other keys`() {
        CompanyObserver().startObserver()
        assert(observerSlot.isCaptured)
        observerSlot.captured.invoke(
            StateChange.KeyValue(ProfileKey.CUSTOM("test_key"), "test_value")
        )
        verify(inverse = true) { Klaviyo.reInitializeInAppForms() }
    }
}
