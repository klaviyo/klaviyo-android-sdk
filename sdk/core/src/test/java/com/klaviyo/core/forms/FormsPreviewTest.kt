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

    @Test
    fun `strips the preview parameter and keeps the rest of the link`() {
        assertEquals(
            "https://example.com/p?utm_source=qr&x=1#frag",
            FormsPreview.withoutPreviewTokenParameter(
                "https://example.com/p?utm_source=qr&klaviyo-forms-preview=$token&x=1#frag"
            )
        )
        assertEquals(
            "myapp://home",
            FormsPreview.withoutPreviewTokenParameter("myapp://home?klaviyo-forms-preview=$token")
        )
        assertEquals(
            "myapp://home?a=1",
            FormsPreview.withoutPreviewTokenParameter("myapp://home?a=1")
        )
        assertEquals("not a uri", FormsPreview.withoutPreviewTokenParameter("not a uri"))
    }

    @Test
    fun `reads and strips the preview key in push key value pairs`() {
        val json = """{"klaviyo-forms-preview":"$token","other":"1"}"""
        assertEquals(token, FormsPreview.previewTokenFromKeyValuePairs(json))
        assertNull(FormsPreview.previewTokenFromKeyValuePairs("""{"other":"1"}"""))
        assertNull(FormsPreview.previewTokenFromKeyValuePairs("not json"))
        assertNull(FormsPreview.previewTokenFromKeyValuePairs(null))

        val stripped = FormsPreview.keyValuePairsWithoutPreviewToken(json)
        assertFalse(stripped.contains(token))
        assertTrue(stripped.contains("other"))
        assertEquals("not json", FormsPreview.keyValuePairsWithoutPreviewToken("not json"))
    }
}
