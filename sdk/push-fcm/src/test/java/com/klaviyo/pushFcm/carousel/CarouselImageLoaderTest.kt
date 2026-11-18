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
import io.mockk.slot
import io.mockk.unmockkStatic
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
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
 * Network is never used: [URL.openConnection] is stubbed to return an in-memory body or to throw.
 */
internal class CarouselImageLoaderTest : BaseTest() {
    private companion object {
        const val TAG = "carousel-tag"

        /** Mirrors the loader's [CarouselImageLoader.MAX_BITMAP_BYTES] ceiling (600 KiB). */
        const val MAX_BITMAP_BYTES = 600 * 1024

        /** Mirrors the loader's [CarouselImageLoader.MAX_DOWNLOAD_BYTES] body cap (5 MiB). */
        const val MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024

        /** Mirrors the loader's per-socket timeout, half of the shared download budget. */
        const val SOCKET_TIMEOUT_MS = DOWNLOAD_TIMEOUT_MS / 2
    }

    /**
     * Invoke the loader's private `capToBudget`, which is where the 600 KiB ceiling is enforced.
     * It returns the decoded bitmap unchanged when in budget, so it cannot be exercised through
     * the public surface alone.
     */
    @Suppress("UNCHECKED_CAST")
    private fun capToBudget(bitmap: Bitmap, position: Int): Bitmap {
        val method = CarouselImageLoader::class.java.declaredMethods
            .first { it.name == "capToBudget" }
        method.isAccessible = true
        return method.invoke(CarouselImageLoader, bitmap, position) as Bitmap
    }

    /** One row of the reviewer's bitmap-budget table. */
    private data class ReviewedCard(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val sampleSize: Int,
        val decodedWidth: Int,
        val decodedHeight: Int,
        val finalWidth: Int,
        val finalHeight: Int
    )

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

    /** The connection a mocked URL hands back; captured so the timeout/read assertions can use it. */
    private fun connectionFor(bytes: ByteArray): URLConnection = mockk<URLConnection>(
        relaxed = true
    ).apply {
        every { getInputStream() } returns ByteArrayInputStream(bytes)
    }

    /** A URL whose download returns [bytes], so decode is the only remaining step. */
    private fun downloadableUrl(bytes: ByteArray = byteArrayOf(1)) =
        mockk<URL>(relaxed = true).apply {
            every { protocol } returns "https"
            every { openConnection() } returns connectionFor(bytes)
        }

    /** A URL whose connection cannot be opened. */
    private fun failingUrl() = mockk<URL>(relaxed = true).apply {
        every { protocol } returns "https"
        every { openConnection() } throws IOException("boom")
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
    fun `thumbnail is null when the card's image is not cached`() {
        assertNull(CarouselImageLoader.thumbnail(TAG, 1))
    }

    @Test
    fun `thumbnail center-crops the cached image to a square, caches it and keeps the source`() {
        val source = mockk<Bitmap>(relaxed = true)
        every { source.width } returns 512
        every { source.height } returns 256
        stubDecode().let { decoded ->
            every { decoded.width } returns 512
            every { decoded.height } returns 256
        }
        CarouselImageLoader.loadOne(TAG, card(1, downloadableUrl()))
        val cachedSource = CarouselImageLoader.get(TAG, 1)

        val square = mockk<Bitmap>(relaxed = true)
        val scaled = mockk<Bitmap>(relaxed = true)
        var cropX = -1
        var cropSide = -1
        mockkStatic(Bitmap::class)
        try {
            every { Bitmap.createBitmap(any<Bitmap>(), any(), any(), any(), any()) } answers {
                cropX = secondArg()
                cropSide = arg(3)
                square
            }
            every { Bitmap.createScaledBitmap(square, 192, 192, any()) } returns scaled

            val first = CarouselImageLoader.thumbnail(TAG, 1)
            val second = CarouselImageLoader.thumbnail(TAG, 1)

            assertTrue(first === scaled)
            assertTrue("the thumbnail is cached", second === scaled)
            assertEquals("crops the middle 256 of 512", 128, cropX)
            assertEquals(256, cropSide)
            io.mockk.verify(exactly = 1) {
                Bitmap.createBitmap(any<Bitmap>(), any(), any(), any(), any())
            }
            io.mockk.verify { square.recycle() }
            io.mockk.verify(exactly = 0) { cachedSource?.recycle() }
            assertTrue(
                "the full image stays cached",
                CarouselImageLoader.get(TAG, 1) === cachedSource
            )
        } finally {
            unmockkStatic(Bitmap::class)
        }
    }

    @Test
    fun `evict drops a tag's thumbnails too`() {
        stubDecode()
        CarouselImageLoader.loadOne(TAG, card(1, downloadableUrl()))
        mockkStatic(Bitmap::class)
        try {
            every {
                Bitmap.createBitmap(any<Bitmap>(), any(), any(), any(), any())
            } returns mockk(relaxed = true)
            every {
                Bitmap.createScaledBitmap(any(), any(), any(), any())
            } returns mockk(relaxed = true)
            CarouselImageLoader.thumbnail(TAG, 1)

            CarouselImageLoader.evict(TAG)

            assertTrue(entries.isEmpty())
        } finally {
            unmockkStatic(Bitmap::class)
        }
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

    // ---------------------------------------------------------------------------------------
    // Bitmap budget (reviewer hardening, c4cae3436c)
    // ---------------------------------------------------------------------------------------

    @Test
    fun `sampling plus the cap keeps every reviewed source within 600 KiB`() {
        // The reviewer's budget table, end to end: sample size for the source, then the final
        // dimensions and byte count after capToBudget. ARGB_8888 is 4 bytes per pixel.
        val reviewed = listOf(
            // source w, source h, expected sample, post-sample w, post-sample h, final w, final h
            ReviewedCard(512, 512, 1, 512, 512, 391, 391),
            ReviewedCard(1000, 1000, 2, 500, 500, 391, 391),
            ReviewedCard(4000, 3000, 8, 500, 375, 452, 339),
            ReviewedCard(300, 2000, 4, 75, 500, 75, 500)
        )

        reviewed.forEach { case ->
            assertEquals(
                "sample size for ${case.sourceWidth}×${case.sourceHeight}",
                case.sampleSize,
                CarouselImageLoader.calculateInSampleSize(
                    case.sourceWidth,
                    case.sourceHeight,
                    512,
                    256
                )
            )

            val source = mockk<Bitmap>(relaxed = true)
            every { source.width } returns case.decodedWidth
            every { source.height } returns case.decodedHeight
            every { source.byteCount } returns case.decodedWidth * case.decodedHeight * 4

            val scaled = mockk<Bitmap>(relaxed = true)
            every { scaled.width } returns case.finalWidth
            every { scaled.height } returns case.finalHeight
            every { scaled.byteCount } returns case.finalWidth * case.finalHeight * 4

            var requestedWidth = 0
            var requestedHeight = 0
            mockkStatic(Bitmap::class)
            try {
                every {
                    Bitmap.createScaledBitmap(any(), any(), any(), any())
                } answers {
                    requestedWidth = secondArg()
                    requestedHeight = thirdArg()
                    scaled
                }

                val capped = capToBudget(source, position = 1)

                assertEquals(
                    "final width for ${case.sourceWidth}×${case.sourceHeight}",
                    case.finalWidth,
                    requestedWidth.takeIf { it > 0 } ?: capped.width
                )
                assertEquals(
                    "final height for ${case.sourceWidth}×${case.sourceHeight}",
                    case.finalHeight,
                    requestedHeight.takeIf { it > 0 } ?: capped.height
                )
                assertTrue(
                    "${case.sourceWidth}×${case.sourceHeight} must fit 600 KiB, got ${capped.byteCount}",
                    capped.byteCount <= MAX_BITMAP_BYTES
                )
            } finally {
                unmockkStatic(Bitmap::class)
            }
        }
    }

    @Test
    fun `capToBudget leaves a bitmap already under budget untouched`() {
        val source = mockk<Bitmap>(relaxed = true)
        every { source.byteCount } returns 611_524

        val capped = capToBudget(source, position = 1)

        assertTrue("an in-budget bitmap must be returned as-is", capped === source)
        io.mockk.verify(exactly = 0) { source.recycle() }
    }

    @Test
    fun `capToBudget scales and recycles an oversized bitmap`() {
        val source = mockk<Bitmap>(relaxed = true)
        every { source.width } returns 500
        every { source.height } returns 500
        every { source.byteCount } returns 1_000_000
        val scaled = mockk<Bitmap>(relaxed = true)
        every { scaled.width } returns 391
        every { scaled.height } returns 391
        every { scaled.byteCount } returns 611_524

        var requestedWidth = 0
        var requestedHeight = 0
        mockkStatic(Bitmap::class)
        try {
            every {
                Bitmap.createScaledBitmap(any(), any(), any(), any())
            } answers {
                requestedWidth = secondArg()
                requestedHeight = thirdArg()
                scaled
            }

            val capped = capToBudget(source, position = 1)

            assertTrue(capped === scaled)
            assertEquals("a 500×500 source shrinks to 391 wide", 391, requestedWidth)
            assertEquals("a 500×500 source shrinks to 391 tall", 391, requestedHeight)
            io.mockk.verify { source.recycle() }
        } finally {
            unmockkStatic(Bitmap::class)
        }
    }

    @Test
    fun `capToBudget never asks for a zero-sized bitmap`() {
        // A very wide, very short source could round a scaled dimension to 0; the helper clamps
        // both dimensions to at least 1 so Bitmap.scale cannot throw.
        val source = mockk<Bitmap>(relaxed = true)
        every { source.width } returns 100_000
        every { source.height } returns 2
        // Above the 600 KiB budget, so the scale path runs.
        every { source.byteCount } returns 100_000 * 2 * 4
        val scaled = mockk<Bitmap>(relaxed = true)
        every { scaled.byteCount } returns 1024

        var requestedWidth = 0
        var requestedHeight = 0
        mockkStatic(Bitmap::class)
        try {
            every {
                Bitmap.createScaledBitmap(any(), any(), any(), any())
            } answers {
                requestedWidth = secondArg()
                requestedHeight = thirdArg()
                scaled
            }

            capToBudget(source, position = 1)

            assertTrue("width must be positive, got $requestedWidth", requestedWidth >= 1)
            assertTrue("height must be positive, got $requestedHeight", requestedHeight >= 1)
        } finally {
            unmockkStatic(Bitmap::class)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Download limits (reviewer hardening, c4cae3436c)
    // ---------------------------------------------------------------------------------------

    @Test
    fun `download sets connect and read timeouts on the connection`() {
        val connectTimeout = slot<Int>()
        val readTimeout = slot<Int>()
        val connection = mockk<URLConnection>(relaxed = true).apply {
            every { getInputStream() } returns ByteArrayInputStream(byteArrayOf(1))
            every { setConnectTimeout(capture(connectTimeout)) } returns Unit
            every { setReadTimeout(capture(readTimeout)) } returns Unit
        }
        val url = mockk<URL>(relaxed = true).apply {
            every { protocol } returns "https"
            every { openConnection() } returns connection
        }

        CarouselImageLoader.loadOne(TAG, card(1, url))

        assertEquals(SOCKET_TIMEOUT_MS, connectTimeout.captured)
        assertEquals(SOCKET_TIMEOUT_MS, readTimeout.captured)
    }

    @Test
    fun `a body over 5 MiB is rejected`() {
        // One byte past the cap; readBytes must abandon before decoding it.
        val tooBig = ByteArray(MAX_DOWNLOAD_BYTES + 1)

        val result = CarouselImageLoader.loadOne(TAG, card(1, downloadableUrl(tooBig)))

        assertNull("an oversized body must not produce a bitmap", result)
        assertNull(CarouselImageLoader.get(TAG, 1))
    }

    @Test
    fun `a body exactly at 5 MiB is accepted`() {
        // Boundary: the cap is "greater than", not "greater than or equal".
        val bitmap = stubDecode()

        val result = CarouselImageLoader.loadOne(
            TAG,
            card(1, downloadableUrl(ByteArray(MAX_DOWNLOAD_BYTES)))
        )

        assertEquals(bitmap, result)
    }

    @Test
    fun `a zero-length read ends the body instead of spinning`() {
        // A broken stream that keeps reporting 0 (rather than -1) must terminate the loop. If it
        // spun, this call would never return and the test would time out.
        val zeroThenEof = mockk<InputStream>()
        var reads = 0
        every { zeroThenEof.read(any()) } answers {
            reads++
            if (reads <= 3) 0 else -1
        }
        val streamUrl = mockk<URL>(relaxed = true).apply {
            every { protocol } returns "https"
            every { openConnection() } returns mockk<URLConnection>(relaxed = true).apply {
                every { getInputStream() } returns zeroThenEof
            }
        }

        val result = CarouselImageLoader.loadOne(TAG, card(1, streamUrl))

        // The first non-positive read ends the body, so the stream is read exactly once.
        assertEquals("a 0-byte read must break the loop immediately", 1, reads)
        assertNull("no bytes means nothing to decode", result)
    }

    // ---------------------------------------------------------------------------------------
    // Per-card isolation and log hygiene (reviewer hardening, c4cae3436c)
    // ---------------------------------------------------------------------------------------

    @Test
    fun `loadAll keeps the other cards when one download fails`() {
        val bitmap = stubDecode()

        val result = CarouselImageLoader.loadAll(
            TAG,
            listOf(card(1, downloadableUrl()), card(2, failingUrl()), card(3, downloadableUrl()))
        )

        assertEquals("cards 1 and 3 must survive card 2's failure", setOf(1, 3), result.keys)
        assertEquals(bitmap, result[1])
        assertEquals(bitmap, result[3])
    }

    @Test
    fun `loadAll keeps the other cards when one image cannot be decoded`() {
        // Card 1 decodes; card 2's bounds pass reports an unusable size. Position is keyed off the
        // sampled options, so a single stub serves both cards without any call ordering.
        val bitmap = mockk<Bitmap>(relaxed = true)
        val undecodable = mockk<URL>(relaxed = true).apply {
            every { protocol } returns "https"
            every { openConnection() } returns mockk<URLConnection>(relaxed = true).apply {
                // A body whose first byte identifies the undecodable card.
                every { getInputStream() } returns ByteArrayInputStream(byteArrayOf(2))
            }
        }
        val decodable = mockk<URL>(relaxed = true).apply {
            every { protocol } returns "https"
            every { openConnection() } returns mockk<URLConnection>(relaxed = true).apply {
                every { getInputStream() } returns ByteArrayInputStream(byteArrayOf(1))
            }
        }
        every { BitmapFactory.decodeByteArray(any(), any(), any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val options = invocation.args[3] as BitmapFactory.Options
            if (options.inJustDecodeBounds) {
                options.outWidth = if (bytes.first() == 2.toByte()) -1 else 1024
                options.outHeight = if (bytes.first() == 2.toByte()) -1 else 512
                null
            } else {
                bitmap
            }
        }

        val result = CarouselImageLoader.loadAll(
            TAG,
            listOf(card(1, decodable), card(2, undecodable))
        )

        assertEquals("only the decodable card should survive", setOf(1), result.keys)
    }

    @Test
    fun `a failed download logs the scheme but never the URL`() {
        val secretHost = "cdn.secret-host.example.com"
        val url = mockk<URL>(relaxed = true)
        every { url.protocol } returns "https"
        every { url.host } returns secretHost
        every { url.toString() } returns "https://$secretHost/card.png"
        every { url.toExternalForm() } returns "https://$secretHost/card.png"
        every { url.openConnection() } throws IOException(
            "failed reading https://$secretHost/card.png"
        )

        val messages = mutableListOf<String>()
        val throwables = mutableListOf<Throwable?>()
        every { spyLog.warning(any(), any()) } answers {
            messages.add(firstArg())
            throwables.add(secondArg())
        }

        CarouselImageLoader.loadOne(TAG, card(1, url))

        // The warning names the position and scheme, and passes no throwable (whose message could
        // embed the URL).
        assertTrue("expected a warning, got $messages", messages.isNotEmpty())
        val warning = messages.first()
        assertTrue(
            "should name the card position: $warning",
            warning.contains("card 1", ignoreCase = true)
        )
        assertTrue("should name the scheme: $warning", warning.contains("scheme: https"))
        assertTrue("must not log the host: $warning", !warning.contains(secretHost))
        assertTrue("must not pass a throwable", throwables.all { it == null })
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
