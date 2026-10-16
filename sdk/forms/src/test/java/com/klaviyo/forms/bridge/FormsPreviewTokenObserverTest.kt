package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.preview.FormsPreviewTokenStore
import com.klaviyo.forms.preview.OTHER_PREVIEW_TOKEN
import com.klaviyo.forms.preview.VALID_PREVIEW_TOKEN
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class FormsPreviewTokenObserverTest : BaseTest() {

    private val mockJsBridge = mockk<JsBridge>(relaxed = true)
    private val store = FormsPreviewTokenStore()
    private val observer = FormsPreviewTokenObserver()

    @Before
    override fun setup() {
        super.setup()
        Registry.register<JsBridge>(mockJsBridge)
        Registry.register<FormsPreviewTokenStore>(store)
    }

    @After
    override fun cleanup() {
        observer.stopObserver()
        Registry.unregister<JsBridge>()
        Registry.unregister<FormsPreviewTokenStore>()
        super.cleanup()
    }

    @Test
    fun `start does not push a token already rendered into the template`() {
        store.setToken(VALID_PREVIEW_TOKEN)
        store.tokenForTemplate()

        observer.startObserver()

        verify(exactly = 0) { mockJsBridge.formsPreviewTokenMutation(any()) }
    }

    @Test
    fun `start does not push when no token was rendered or set`() {
        store.tokenForTemplate()
        observer.startObserver()

        verify(exactly = 0) { mockJsBridge.formsPreviewTokenMutation(any()) }
    }

    @Test
    fun `start pushes a token set after the template was rendered`() {
        store.tokenForTemplate()
        store.setToken(VALID_PREVIEW_TOKEN)

        observer.startObserver()

        verify(exactly = 1) { mockJsBridge.formsPreviewTokenMutation(VALID_PREVIEW_TOKEN) }
    }

    @Test
    fun `start clears a token cleared after the template was rendered`() {
        store.setToken(VALID_PREVIEW_TOKEN)
        store.tokenForTemplate()
        store.clear()

        observer.startObserver()

        verify(exactly = 1) { mockJsBridge.formsPreviewTokenMutation("") }
    }

    @Test
    fun `token changes while observing reach the webview`() {
        store.tokenForTemplate()
        observer.startObserver()

        store.setToken(VALID_PREVIEW_TOKEN)
        store.setToken(OTHER_PREVIEW_TOKEN)
        store.setToken(null)

        verify(exactly = 1) { mockJsBridge.formsPreviewTokenMutation(VALID_PREVIEW_TOKEN) }
        verify(exactly = 1) { mockJsBridge.formsPreviewTokenMutation(OTHER_PREVIEW_TOKEN) }
        verify(exactly = 1) { mockJsBridge.formsPreviewTokenMutation("") }
    }

    @Test
    fun `token changes after stopping are not delivered`() {
        observer.startObserver()
        observer.stopObserver()

        store.setToken(VALID_PREVIEW_TOKEN)

        verify(exactly = 0) { mockJsBridge.formsPreviewTokenMutation(any()) }
    }
}
