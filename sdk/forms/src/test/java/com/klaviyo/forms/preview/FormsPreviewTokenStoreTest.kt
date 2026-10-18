package com.klaviyo.forms.preview

import com.klaviyo.fixtures.BaseTest
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormsPreviewTokenStoreTest : BaseTest() {

    private val store = FormsPreviewTokenStore()
    private var notifications = 0
    private val observer: FormsPreviewTokenObserver = { notifications++ }

    @Test
    fun `stores a valid token and notifies observers`() {
        store.onTokenChange(observer)
        store.setToken(VALID_PREVIEW_TOKEN)

        assertEquals(VALID_PREVIEW_TOKEN, store.token)
        assertEquals(1, notifications)
    }

    @Test
    fun `setting the same token again does not notify`() {
        store.onTokenChange(observer)
        store.setToken(VALID_PREVIEW_TOKEN)
        store.setToken(VALID_PREVIEW_TOKEN)

        assertEquals(1, notifications)
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
    fun `clear removes the token and notifies`() {
        store.setToken(VALID_PREVIEW_TOKEN)
        store.onTokenChange(observer)
        store.clear()

        assertNull(store.token)
        assertEquals(1, notifications)
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

    @Test
    fun `removed observers are not notified`() {
        store.onTokenChange(observer)
        store.offTokenChange(observer)
        store.setToken(VALID_PREVIEW_TOKEN)

        assertEquals(0, notifications)
    }

    @Test
    fun `tokenForTemplate records the rendered token`() {
        assertNull(store.renderedToken)
        store.setToken(VALID_PREVIEW_TOKEN)

        assertEquals(VALID_PREVIEW_TOKEN, store.tokenForTemplate())
        assertEquals(VALID_PREVIEW_TOKEN, store.renderedToken)

        store.clear()
        assertEquals(VALID_PREVIEW_TOKEN, store.renderedToken)
        assertNull(store.tokenForTemplate())
        assertNull(store.renderedToken)
    }

    @Test
    fun `end clears the token and blocks it from config but not from the setter`() {
        store.setToken(VALID_PREVIEW_TOKEN)
        store.end()
        assertNull(store.token)

        store.applyConfigToken(VALID_PREVIEW_TOKEN)
        assertNull(store.token)

        store.applyConfigToken(OTHER_PREVIEW_TOKEN)
        assertEquals(OTHER_PREVIEW_TOKEN, store.token)

        store.setToken(VALID_PREVIEW_TOKEN)
        assertEquals(VALID_PREVIEW_TOKEN, store.token)
    }

    @Test
    fun `applyConfigToken leaves the token for null or blank and clears it for malformed`() {
        store.setToken(VALID_PREVIEW_TOKEN)
        store.applyConfigToken(null)
        store.applyConfigToken("  ")
        assertEquals(VALID_PREVIEW_TOKEN, store.token)

        store.applyConfigToken("<script>")
        assertNull(store.token)
    }
}
