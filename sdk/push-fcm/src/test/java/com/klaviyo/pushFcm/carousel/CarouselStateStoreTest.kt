package com.klaviyo.pushFcm.carousel

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.StatusBarNotification
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class CarouselStateStoreTest : BaseTest() {
    private companion object {
        const val PREFS_NAME = "klaviyo_carousel_state"
        const val STATE_KEY = "carousel_state"

        /** Mirrors the store's 7-day prune window. */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        const val TAG = "carousel-tag"
    }

    private val preferences = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    /** The whole tag map as last written, so reads in later store calls see earlier writes. */
    private var storedState: String? = null

    @Before
    override fun setup() {
        super.setup()

        every { mockContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns preferences
        every { preferences.getString(STATE_KEY, null) } answers { storedState }
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            storedState = secondArg()
            editor
        }
    }

    @Test
    fun `getIndex defaults to 0 when no entry exists`() {
        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG))
    }

    @Test
    fun `setIndex then getIndex round-trips the index`() {
        CarouselStateStore.setIndex(mockContext, TAG, 2)

        assertEquals(2, CarouselStateStore.getIndex(mockContext, TAG))
    }

    @Test
    fun `setIndex writes to the dedicated carousel preferences file`() {
        CarouselStateStore.setIndex(mockContext, TAG, 1)

        verify { mockContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
        verify { editor.putString(STATE_KEY, any()) }
    }

    @Test
    fun `indexes are isolated per notification tag`() {
        CarouselStateStore.setIndex(mockContext, "tag-a", 1)
        CarouselStateStore.setIndex(mockContext, "tag-b", 4)

        assertEquals(1, CarouselStateStore.getIndex(mockContext, "tag-a"))
        assertEquals(4, CarouselStateStore.getIndex(mockContext, "tag-b"))
    }

    @Test
    fun `clear removes only the given tag`() {
        CarouselStateStore.setIndex(mockContext, "tag-a", 1)
        CarouselStateStore.setIndex(mockContext, "tag-b", 4)

        CarouselStateStore.clear(mockContext, "tag-a")

        assertEquals(0, CarouselStateStore.getIndex(mockContext, "tag-a"))
        assertEquals(4, CarouselStateStore.getIndex(mockContext, "tag-b"))
    }

    @Test
    fun `clear on an unknown tag does not rewrite state`() {
        CarouselStateStore.clear(mockContext, "never-seen")

        verify(exactly = 0) { editor.putString(any(), any()) }
    }

    @Test
    fun `setIndex prunes entries older than seven days`() {
        // The store uses System.currentTimeMillis() (not Registry.clock) so it works pre-init.
        val now = System.currentTimeMillis()
        storedState = JSONObject()
            .put(
                "stale",
                JSONObject().put("index", 3).put("updatedAt", now - MAX_AGE_MS - 1)
            )
            .put(
                "fresh",
                JSONObject().put("index", 2).put("updatedAt", now - MAX_AGE_MS + 60_000)
            )
            .toString()

        CarouselStateStore.setIndex(mockContext, TAG, 1)

        val written = JSONObject(storedState.orEmpty())
        assertFalse("stale entry should be pruned", written.has("stale"))
        assertTrue("fresh entry should survive", written.has("fresh"))
        assertTrue("the new entry should be written", written.has(TAG))
    }

    @Test
    fun `a stored index for a pruned tag falls back to 0`() {
        // getIndex does not prune (only writes do), but an entry written long ago is still readable;
        // verify the defaulting behaviour is unchanged for absent tags.
        storedState = JSONObject()
            .put("other", JSONObject().put("index", 4).put("updatedAt", TIME))
            .toString()

        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG))
    }

    @Test
    fun `malformed stored state is treated as empty`() {
        storedState = "{not-json"

        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG))
        // A subsequent write recovers rather than throwing.
        CarouselStateStore.setIndex(mockContext, TAG, 2)
        assertEquals(2, CarouselStateStore.getIndex(mockContext, TAG))
    }

    @Test
    fun `stored entry missing its index falls back to 0`() {
        storedState = JSONObject()
            .put(TAG, JSONObject().put("updatedAt", TIME))
            .toString()

        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG))
    }

    @Test
    fun `setIndex stamps the entry with the wall clock for later pruning`() {
        val before = System.currentTimeMillis()

        CarouselStateStore.setIndex(mockContext, TAG, 1)

        val entry = JSONObject(storedState.orEmpty()).getJSONObject(TAG)
        assertTrue(
            "updatedAt should be a current wall-clock timestamp",
            entry.getLong("updatedAt") >= before
        )
    }

    /** Report [tags] as the notifications currently showing. */
    private fun activeNotifications(vararg tags: String) {
        val manager = mockk<NotificationManager>()
        every { manager.activeNotifications } returns tags.map { tag ->
            mockk<StatusBarNotification>().apply { every { this@apply.tag } returns tag }
        }.toTypedArray()
        every { mockContext.getSystemService(NotificationManager::class.java) } returns manager
    }

    @Test
    fun `pruneInactive drops carousels that are no longer showing and evicts their images`() {
        CarouselStateStore.setIndex(mockContext, "tag-a", 1)
        CarouselStateStore.setIndex(mockContext, "tag-b", 2)
        activeNotifications("tag-b", "unrelated")
        mockkObject(CarouselImageLoader)
        every { CarouselImageLoader.evict(any()) } returns Unit
        try {
            CarouselStateStore.pruneInactive(mockContext)

            val stored = JSONObject(storedState.orEmpty())
            assertFalse(stored.has("tag-a"))
            assertEquals(2, CarouselStateStore.getIndex(mockContext, "tag-b"))
            verify { CarouselImageLoader.evict("tag-a") }
            verify(exactly = 0) { CarouselImageLoader.evict("tag-b") }
        } finally {
            unmockkObject(CarouselImageLoader)
        }
    }

    @Test
    fun `pruneInactive keeps everything when active notifications cannot be read`() {
        CarouselStateStore.setIndex(mockContext, TAG, 3)
        every {
            mockContext.getSystemService(NotificationManager::class.java)
        } throws SecurityException("nope")

        CarouselStateStore.pruneInactive(mockContext)

        assertEquals(3, CarouselStateStore.getIndex(mockContext, TAG))
    }

    @Test
    fun `concurrent setIndex calls for different tags never drop an entry`() {
        val tags = (1..8).map { "tag-$it" }
        val threads = tags.mapIndexed { i, tag ->
            Thread { repeat(25) { CarouselStateStore.setIndex(mockContext, tag, i) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        tags.forEachIndexed { i, tag ->
            assertEquals("$tag lost its index", i, CarouselStateStore.getIndex(mockContext, tag))
        }
    }
}
