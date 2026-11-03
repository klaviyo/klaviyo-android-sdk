package com.klaviyo.forms.preview

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormsPreviewTokenStoreTest : BaseTest() {

    private val store = FormsPreviewTokenStore()

    @Test
    fun `stores a valid token and reports the change`() {
        assertTrue(store.setToken(VALID_PREVIEW_TOKEN))
        assertEquals(VALID_PREVIEW_TOKEN, store.token)
    }

    @Test
    fun `setting the same token again reports no change`() {
        store.setToken(VALID_PREVIEW_TOKEN)
        assertFalse(store.setToken(VALID_PREVIEW_TOKEN))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        listOf("$VALID_PREVIEW_TOKEN\n", "  $VALID_PREVIEW_TOKEN ", "\t$VALID_PREVIEW_TOKEN").forEach {
            store.clear()
            store.setToken(it)
            assertEquals(VALID_PREVIEW_TOKEN, store.token)
        }
    }

    @Test
    fun `null and blank clear the token`() {
        listOf(null, "", "   ").forEach { clearValue ->
            store.setToken(VALID_PREVIEW_TOKEN)
            store.setToken(clearValue)
            assertNull(store.token)
        }
    }

    @Test
    fun `clear removes the token`() {
        store.setToken(VALID_PREVIEW_TOKEN)

        assertTrue(store.clear())
        assertNull(store.token)
        assertFalse(store.clear())
    }

    @Test
    fun `malformed tokens are dropped with a warning that omits the token`() {
        val malformed = listOf(
            VALID_PREVIEW_TOKEN.dropLast(1), // too short
            VALID_PREVIEW_TOKEN + "a", // too long
            VALID_PREVIEW_TOKEN.dropLast(1) + "=", // invalid character
            VALID_PREVIEW_TOKEN.dropLast(2) + "'>", // markup injection attempt
            VALID_PREVIEW_TOKEN.take(20) + " " + VALID_PREVIEW_TOKEN.drop(21) // inner whitespace
        )

        malformed.forEach { token ->
            store.setToken(OTHER_PREVIEW_TOKEN)
            store.setToken(token)
            assertNull("Expected $token to be rejected", store.token)
            verify { spyLog.warning(not(match { it.contains(token) }), null) }
        }
        verify(exactly = malformed.size) { spyLog.warning(any(), null) }
    }

    @Test
    fun `token value never reaches any log`() {
        store.setToken(VALID_PREVIEW_TOKEN)
        store.setToken(OTHER_PREVIEW_TOKEN)
        store.clear()

        listOf(VALID_PREVIEW_TOKEN, OTHER_PREVIEW_TOKEN).forEach { token ->
            verify(exactly = 0) { spyLog.verbose(match { it.contains(token) }, any()) }
            verify(exactly = 0) { spyLog.debug(match { it.contains(token) }, any()) }
            verify(exactly = 0) { spyLog.info(match { it.contains(token) }, any()) }
            verify(exactly = 0) { spyLog.warning(match { it.contains(token) }, any()) }
            verify(exactly = 0) { spyLog.error(match { it.contains(token) }, any()) }
        }
    }
}
