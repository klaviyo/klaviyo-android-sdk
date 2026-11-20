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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        const val UID = "uid-a"
        const val NEW_UID = "uid-b"
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
        activeNotifications(TAG)
    }

    /** Tags of the notifications currently showing; [show] adds to it. */
    private val activeTags = mutableSetOf<String>()

    /** Report exactly [tags] as the notifications currently showing. */
    private fun activeNotifications(vararg tags: String) {
        activeTags.clear()
        activeTags += tags
        val manager = mockk<NotificationManager>()
        every { manager.activeNotifications } answers {
            activeTags.map { tag ->
                mockk<StatusBarNotification>().apply { every { this@apply.tag } returns tag }
            }.toTypedArray()
        }
        every { mockContext.getSystemService(NotificationManager::class.java) } returns manager
    }

    /** Post [uid] under [tag] at card 1, then move it to [index]. */
    private fun show(tag: String, uid: String, index: Int = 0) {
        activeTags += tag
        CarouselStateStore.replace(mockContext, tag, uid) {}
        if (index != 0) {
            CarouselStateStore.advance(mockContext, tag, uid, index) {}
        }
    }

    private fun stored(): JSONObject = JSONObject(storedState.orEmpty())

    @Test
    fun `getIndex defaults to 0 when no entry exists`() {
        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `advance then getIndex round-trips the index`() {
        show(TAG, UID, index = 2)

        assertEquals(2, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `writes go to the dedicated carousel preferences file`() {
        show(TAG, UID)

        verify { mockContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
        verify { editor.putString(STATE_KEY, any()) }
    }

    @Test
    fun `indexes are isolated per notification tag`() {
        show("tag-a", "uid-1", index = 1)
        show("tag-b", "uid-2", index = 4)

        assertEquals(1, CarouselStateStore.getIndex(mockContext, "tag-a", "uid-1"))
        assertEquals(4, CarouselStateStore.getIndex(mockContext, "tag-b", "uid-2"))
    }

    @Test
    fun `replace posts before recording the new carousel at card 1`() {
        show(TAG, UID, index = 2)
        var postedWhileOldIndexKept = false

        CarouselStateStore.replace(mockContext, TAG, NEW_UID) {
            // The old carousel is still the one showing until this post lands.
            postedWhileOldIndexKept = CarouselStateStore.getIndex(mockContext, TAG, UID) == 2
        }

        assertTrue(postedWhileOldIndexKept)
        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG, NEW_UID))
    }

    @Test
    fun `replace returns the replaced uid so its images can be dropped`() {
        show(TAG, UID)

        assertEquals(UID, CarouselStateStore.replace(mockContext, TAG, NEW_UID) {})
        assertNull(CarouselStateStore.replace(mockContext, "fresh-tag", NEW_UID) {})
    }

    @Test
    fun `replace writes nothing when the post throws`() {
        show(TAG, UID, index = 2)

        assertThrows(IllegalStateException::class.java) {
            CarouselStateStore.replace(mockContext, TAG, NEW_UID) { error("notify failed") }
        }

        assertEquals(2, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `a tap from a replaced carousel reads no index and cannot re-post`() {
        show(TAG, UID, index = 1)
        CarouselStateStore.replace(mockContext, TAG, NEW_UID) {}
        var posted = false

        assertNull(CarouselStateStore.getIndex(mockContext, TAG, UID))
        assertFalse(CarouselStateStore.advance(mockContext, TAG, UID, 2) { posted = true })

        assertFalse(posted)
        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG, NEW_UID))
    }

    @Test
    fun `advance drops the re-post when the notification was dismissed`() {
        show(TAG, UID)
        activeNotifications("unrelated")
        var posted = false

        assertFalse(CarouselStateStore.advance(mockContext, TAG, UID, 1) { posted = true })

        assertFalse(posted)
        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `advance still re-posts when active notifications cannot be read`() {
        show(TAG, UID)
        every {
            mockContext.getSystemService(NotificationManager::class.java)
        } throws SecurityException("nope")
        var posted = false

        assertTrue(CarouselStateStore.advance(mockContext, TAG, UID, 1) { posted = true })

        assertTrue(posted)
        assertEquals(1, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `advance keeps the old index when the post throws`() {
        show(TAG, UID, index = 1)

        assertThrows(IllegalStateException::class.java) {
            CarouselStateStore.advance(mockContext, TAG, UID, 2) { error("notify failed") }
        }

        assertEquals(1, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `clear removes only the given tag`() {
        show("tag-a", "uid-1", index = 1)
        show("tag-b", "uid-2", index = 4)

        CarouselStateStore.clear(mockContext, "tag-a", "uid-1")

        assertFalse(stored().has("tag-a"))
        assertEquals(4, CarouselStateStore.getIndex(mockContext, "tag-b", "uid-2"))
    }

    @Test
    fun `clear from a replaced carousel keeps the newer carousel's entry`() {
        show(TAG, UID)
        show(TAG, NEW_UID, index = 1)

        CarouselStateStore.clear(mockContext, TAG, UID)

        assertEquals(1, CarouselStateStore.getIndex(mockContext, TAG, NEW_UID))
    }

    @Test
    fun `clear on an unknown tag does not rewrite state`() {
        CarouselStateStore.clear(mockContext, "never-seen", UID)

        verify(exactly = 0) { editor.putString(any(), any()) }
    }

    @Test
    fun `one unreadable entry is dropped without resetting the other carousels`() {
        storedState = JSONObject()
            .put(
                "tag-good",
                JSONObject().put("uid", UID).put("index", 3)
                    .put("updatedAt", System.currentTimeMillis())
            )
            .put("tag-bad", "not-an-object")
            .toString()

        assertEquals(3, CarouselStateStore.getIndex(mockContext, "tag-good", UID))
        assertEquals(0, CarouselStateStore.getIndex(mockContext, "tag-bad", UID))

        // The next write keeps the good entry instead of persisting an empty map.
        show(TAG, NEW_UID)
        assertEquals(3, CarouselStateStore.getIndex(mockContext, "tag-good", UID))
        assertFalse(stored().has("tag-bad"))
    }

    @Test
    fun `malformed stored state is treated as empty`() {
        storedState = "{not-json"

        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG, UID))
        // A subsequent write recovers rather than throwing.
        show(TAG, UID, index = 2)
        assertEquals(2, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `stored entry missing its index falls back to 0`() {
        storedState = JSONObject()
            .put(TAG, JSONObject().put("uid", UID).put("updatedAt", TIME))
            .toString()

        assertEquals(0, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `writes stamp the entry with the wall clock for later pruning`() {
        val before = System.currentTimeMillis()

        show(TAG, UID)

        assertTrue(
            "updatedAt should be a current wall-clock timestamp",
            stored().getJSONObject(TAG).getLong("updatedAt") >= before
        )
    }

    @Test
    fun `pruneInactive drops carousels that are no longer showing and evicts their images`() {
        show("tag-a", "uid-1", index = 1)
        show("tag-b", "uid-2", index = 2)
        activeNotifications("tag-b", "unrelated")
        mockkObject(CarouselImageLoader)
        every { CarouselImageLoader.evict(any()) } returns Unit
        try {
            CarouselStateStore.pruneInactive(mockContext)

            assertFalse(stored().has("tag-a"))
            assertEquals(2, CarouselStateStore.getIndex(mockContext, "tag-b", "uid-2"))
            verify { CarouselImageLoader.evict("uid-1") }
            verify(exactly = 0) { CarouselImageLoader.evict("uid-2") }
        } finally {
            unmockkObject(CarouselImageLoader)
        }
    }

    @Test
    fun `pruneInactive keeps a carousel still showing however old its entry is`() {
        storedState = JSONObject()
            .put(
                TAG,
                JSONObject().put("uid", UID).put("index", 3)
                    .put("updatedAt", System.currentTimeMillis() - MAX_AGE_MS - 1)
            )
            .toString()

        CarouselStateStore.pruneInactive(mockContext)

        assertEquals(3, CarouselStateStore.getIndex(mockContext, TAG, UID))
    }

    @Test
    fun `pruneInactive falls back to the age cutoff when active notifications cannot be read`() {
        val now = System.currentTimeMillis()
        storedState = JSONObject()
            .put("stale", JSONObject().put("uid", "u1").put("updatedAt", now - MAX_AGE_MS - 1))
            .put(
                "fresh",
                JSONObject().put("uid", "u2").put("index", 3)
                    .put("updatedAt", now - MAX_AGE_MS + 60_000)
            )
            .toString()
        every {
            mockContext.getSystemService(NotificationManager::class.java)
        } throws SecurityException("nope")

        CarouselStateStore.pruneInactive(mockContext)

        assertFalse("stale entry should be pruned", stored().has("stale"))
        assertEquals(3, CarouselStateStore.getIndex(mockContext, "fresh", "u2"))
    }

    @Test
    fun `concurrent writes for different tags never drop an entry`() {
        val tags = (1..8).map { "tag-$it" }
        activeNotifications(*tags.toTypedArray())
        tags.forEach { tag -> CarouselStateStore.replace(mockContext, tag, "uid-$tag") {} }
        // Every index is nonzero, so a dropped entry cannot pass as the default of 0.
        val threads = tags.mapIndexed { i, tag ->
            Thread {
                repeat(25) {
                    CarouselStateStore.advance(mockContext, tag, "uid-$tag", i + 1) {}
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        tags.forEachIndexed { i, tag ->
            assertEquals(
                "$tag lost its index",
                i + 1,
                CarouselStateStore.getIndex(mockContext, tag, "uid-$tag")
            )
        }
    }
}
