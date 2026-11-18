package com.klaviyo.pushFcm.carousel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.pushFcm.KlaviyoNotification.Companion.DOWNLOAD_TIMEOUT_MS
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCardAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import java.net.URL
import java.util.TreeMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CarouselImageLoader].
 *
 * The loader holds a single static [LruCache], and `android.util.LruCache` is a JVM stub that
 * throws "not mocked" on its first touch, so the cache field is replaced with a mock backed by a
 * plain map. The mock reproduces real cache semantics: entries are keyed by the loader's
 * `CardKey(tag, position)` data class, so equality and eviction behave as they would on device.
 *
 * Network is never used: [URL.openStream] is stubbed to return bytes or to throw.
 */
internal class CarouselImageLoaderTest : BaseTest() {
    private companion object {
        const val TAG = "carousel-tag"
    }

    /** Stands in for the cache's backing store; keys are real `CardKey` instances. */
    private val entries = mutableMapOf<Any?, Bitmap>()

    /** Number of [BitmapFactory.decodeByteArray] calls made through [stubDecode]. */
    private var decodeCount = 0

    @Before
    override fun setup() {
        super.setup()
        installFakeCache()
        mockkStatic(BitmapFactory::class)
    }

    @After
    override fun cleanup() {
        unmockkStatic(BitmapFactory::class)
        super.cleanup()
    }

    /**
     * Swap the loader's private static cache for a mock backed by [entries]. The real [LruCache]
     * cannot be constructed in the JVM test environment.
     */
    @Suppress("UNCHECKED_CAST")
    private fun installFakeCache() {
        val cacheField = CarouselImageLoader::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        val raw = mockkClass(cacheField.type.kotlin as kotlin.reflect.KClass<Any>)
            as LruCache<Any?, Any?>
        every { raw.get(any()) } answers { entries[firstArg()] }
        every { raw.put(any(), any()) } answers {
            entries.put(invocation.args[0], invocation.args[1] as Bitmap)
        }
        every { raw.remove(any()) } answers { entries.remove(firstArg()) }
        every { raw.snapshot() } answers {
            val snapshot = TreeMap<Any?, Any?>(compareBy { it.toString() })
            entries.forEach { (key, value) -> snapshot[key] = value }
            snapshot
        }
        setFinalStatic(cacheField, raw)
    }

    private fun card(position: Int, url: URL = mockUrl()) = CarouselCard(
        position = position,
        imageUrl = url,
        title = "Title $position",
        description = "Description $position",
        action = CarouselCardAction.OpenApp
    )

    private fun mockUrl() = mockk<URL>(relaxed = true)

    /** A URL whose download returns one byte, so decode is the only remaining step. */
    private fun downloadableUrl() = mockk<URL>(relaxed = true).apply {
        every { openStream() } returns byteArrayOf(1).inputStream()
    }

    /** A URL whose download fails. */
    private fun failingUrl() = mockk<URL>(relaxed = true).apply {
        every { protocol } returns "https"
        every { openStream() } throws IOException("boom")
    }

    /**
     * Stub [BitmapFactory.decodeByteArray] so the bounds pass reports [width]×[height] and the
     * sampled pass returns a bitmap.
     */
    private fun stubDecode(width: Int = 1024, height: Int = 512): Bitmap {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { BitmapFactory.decodeByteArray(any(), any(), any(), any()) } answers {
            decodeCount++
            val options = invocation.args[3] as BitmapFactory.Options
            if (options.inJustDecodeBounds) {
                options.outWidth = width
                options.outHeight = height
            }
            bitmap
        }
        return bitmap
    }

    @Test
    fun `calculateInSampleSize returns 1 for an image within bounds`() {
        assertEquals(1, CarouselImageLoader.calculateInSampleSize(512, 256, 512, 512))
    }

    @Test
    fun `calculateInSampleSize halves until the long edge fits`() {
        assertEquals(2, CarouselImageLoader.calculateInSampleSize(1024, 512, 512, 512))
        assertEquals(8, CarouselImageLoader.calculateInSampleSize(4096, 1024, 512, 512))
    }

    @Test
    fun `calculateInSampleSize uses the larger requested dimension`() {
        // Long edge 1000 fits a 1024 target but not a 512 one.
        assertEquals(2, CarouselImageLoader.calculateInSampleSize(1000, 800, 512, 512))
        assertEquals(1, CarouselImageLoader.calculateInSampleSize(1000, 800, 1024, 1024))
    }

    @Test
    fun `calculateInSampleSize treats a non-positive target as no sampling`() {
        assertEquals(1, CarouselImageLoader.calculateInSampleSize(4096, 4096, 0, 0))
    }

    @Test
    fun `calculateInSampleSize squares a portrait image by its long edge`() {
        // Portrait 512x2048: long edge 2048 → 4 to bring it to ≤512.
        assertEquals(4, CarouselImageLoader.calculateInSampleSize(512, 2048, 512, 512))
    }

    @Test
    fun `get returns null on a cache miss`() {
        assertNull(CarouselImageLoader.get(TAG, 1))
    }

    @Test
    fun `get returns a bitmap that loadOne cached`() {
        val bitmap = stubDecode()

        CarouselImageLoader.loadOne(TAG, card(1, downloadableUrl()))

        assertEquals(bitmap, CarouselImageLoader.get(TAG, 1))
    }

    @Test
    fun `get is keyed by tag and position`() {
        val byPosition = mutableListOf<Any?>()
        val cache = cacheMock()
        every { cache.get(any()) } answers {
            byPosition.add(firstArg())
            null
        }

        CarouselImageLoader.get(TAG, 1)
        CarouselImageLoader.get(TAG, 1)
        CarouselImageLoader.get(TAG, 2)
        CarouselImageLoader.get("other-tag", 1)

        // Same tag+position maps to an equal key; a different position or tag does not.
        assertEquals(byPosition[0], byPosition[1])
        assertNotEquals(byPosition[0], byPosition[2])
        assertNotEquals(byPosition[0], byPosition[3])
    }

    @Test
    fun `loadAll returns cached bitmaps without downloading`() {
        val bitmap = stubDecode()
        CarouselImageLoader.loadOne(TAG, card(1, downloadableUrl()))
        CarouselImageLoader.loadOne(TAG, card(2, downloadableUrl()))

        val result = CarouselImageLoader.loadAll(TAG, listOf(card(1), card(2)))

        assertEquals(mapOf(1 to bitmap, 2 to bitmap), result)
    }

    @Test
    fun `loadAll downloads and caches a missing card`() {
        val bitmap = stubDecode()

        val result = CarouselImageLoader.loadAll(TAG, listOf(card(1, downloadableUrl())))

        assertEquals(mapOf(1 to bitmap), result)
        assertEquals(bitmap, CarouselImageLoader.get(TAG, 1))
    }

    @Test
    fun `loadAll omits a card whose download fails`() {
        val result = CarouselImageLoader.loadAll(TAG, listOf(card(1, failingUrl())))

        assertTrue("failed card should be absent", result.isEmpty())
        assertNull(CarouselImageLoader.get(TAG, 1))
    }

    @Test
    fun `loadAll omits a card whose image cannot be decoded`() {
        every { BitmapFactory.decodeByteArray(any(), any(), any(), any()) } answers {
            (invocation.args[3] as BitmapFactory.Options).apply {
                outWidth = -1
                outHeight = -1
            }
            null
        }

        val result = CarouselImageLoader.loadAll(TAG, listOf(card(1, downloadableUrl())))

        assertTrue(result.isEmpty())
        assertNull(CarouselImageLoader.get(TAG, 1))
    }

    @Test
    fun `loadAll gives up on a download that exceeds the timeout`() {
        // The download blocks well past DOWNLOAD_TIMEOUT_MS, so the wait times out and the card
        // is omitted rather than the notification being held up indefinitely.
        val slowUrl = mockk<URL>(relaxed = true).apply {
            every { protocol } returns "https"
            every { openStream() } answers {
                Thread.sleep(DOWNLOAD_TIMEOUT_MS * 2L)
                byteArrayOf(1).inputStream()
            }
        }

        val startedAt = System.currentTimeMillis()
        val result = CarouselImageLoader.loadAll(TAG, listOf(card(1, slowUrl)))

        val elapsed = System.currentTimeMillis() - startedAt
        assertTrue("timed-out card should be absent", result.isEmpty())
        assertNull(CarouselImageLoader.get(TAG, 1))
        assertTrue(
            "loadAll should bound the wait near DOWNLOAD_TIMEOUT_MS, took ${elapsed}ms",
            elapsed < DOWNLOAD_TIMEOUT_MS * 2
        )
    }

    @Test
    fun `loadAll only downloads the cards that are not cached`() {
        val bitmap = stubDecode()
        CarouselImageLoader.loadOne(TAG, card(1, downloadableUrl()))
        val decodesBefore = decodeCount

        val result = CarouselImageLoader.loadAll(
            TAG,
            listOf(card(1), card(2, downloadableUrl()))
        )

        assertEquals(mapOf(1 to bitmap, 2 to bitmap), result)
        // Card 1 was cached, so only card 2's two decode passes (bounds + sample) ran.
        assertEquals(2, decodeCount - decodesBefore)
    }

    @Test
    fun `loadOne returns a cached bitmap without downloading`() {
        val bitmap = stubDecode()
        CarouselImageLoader.loadOne(TAG, card(1, downloadableUrl()))

        val result = CarouselImageLoader.loadOne(TAG, card(1, failingUrl()))

        assertEquals(bitmap, result)
    }

    @Test
    fun `loadOne returns null when the download fails`() {
        assertNull(CarouselImageLoader.loadOne(TAG, card(1, failingUrl())))
        assertNull(CarouselImageLoader.get(TAG, 1))
    }

    @Test
    fun `evict drops the tag's cached cards but keeps another tag's`() {
        stubDecode()
        CarouselImageLoader.loadAll(
            TAG,
            listOf(card(1, downloadableUrl()), card(2, downloadableUrl()))
        )
        CarouselImageLoader.loadOne("other-tag", card(1, downloadableUrl()))

        CarouselImageLoader.evict(TAG)

        assertNull(CarouselImageLoader.get(TAG, 1))
        assertNull(CarouselImageLoader.get(TAG, 2))
        assertTrue("another tag must survive", CarouselImageLoader.get("other-tag", 1) != null)
    }

    @Test
    fun `evict on an empty cache is a no-op`() {
        CarouselImageLoader.evict(TAG)

        assertTrue(entries.isEmpty())
    }

    /**
     * The installed fake cache mock, so a test can override one method. Actions on it mutate the
     * same [entries] map as the other stubs.
     */
    @Suppress("UNCHECKED_CAST")
    private fun cacheMock(): LruCache<Any?, Any?> =
        CarouselImageLoader::class.java.getDeclaredField("cache").let { field ->
            field.isAccessible = true
            field.get(null) as LruCache<Any?, Any?>
        }
}
