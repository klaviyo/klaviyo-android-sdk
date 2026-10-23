package com.klaviyo.core.forms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormsPreviewTest {
    private val token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123-_6"

    @Test
    fun `reads the preview parameter from custom scheme and https links`() {
        assertEquals(
            token,
            FormsPreview.previewTokenParameter("myapp://home?klaviyo-forms-preview=$token")
        )
        assertEquals(
            token,
            FormsPreview.previewTokenParameter("myapp:///?klaviyo-forms-preview=$token")
        )
        assertEquals(
            token,
            FormsPreview.previewTokenParameter(
                "https://shop.example.com/any/path?klaviyo-forms-preview=$token"
            )
        )
    }

    @Test
    fun `reads the preview parameter among other query parameters`() {
        assertEquals(
            token,
            FormsPreview.previewTokenParameter(
                "https://example.com/p?utm_source=qr&klaviyo-forms-preview=$token&x=1"
            )
        )
    }

    @Test
    fun `returns null without the parameter`() {
        assertNull(FormsPreview.previewTokenParameter("myapp://home"))
        assertNull(FormsPreview.previewTokenParameter("https://example.com/?other=$token"))
        assertNull(
            FormsPreview.previewTokenParameter("https://example.com/#klaviyo-forms-preview=$token")
        )
        assertNull(FormsPreview.previewTokenParameter(null))
        assertNull(FormsPreview.previewTokenParameter("not a uri with spaces"))
    }

    @Test
    fun `returns the raw value without validating it`() {
        assertEquals(
            "short",
            FormsPreview.previewTokenParameter("myapp://x?klaviyo-forms-preview=short")
        )
        assertEquals("", FormsPreview.previewTokenParameter("myapp://x?klaviyo-forms-preview="))
        assertEquals("", FormsPreview.previewTokenParameter("myapp://x?klaviyo-forms-preview"))
    }

    @Test
    fun `validates the token format`() {
        assertTrue(FormsPreview.isValidToken(token))
        assertFalse(FormsPreview.isValidToken(token.dropLast(1)))
        assertFalse(FormsPreview.isValidToken("$token "))
        assertFalse(FormsPreview.isValidToken(token.dropLast(1) + "="))
        assertFalse(FormsPreview.isValidToken(null))
    }
}
