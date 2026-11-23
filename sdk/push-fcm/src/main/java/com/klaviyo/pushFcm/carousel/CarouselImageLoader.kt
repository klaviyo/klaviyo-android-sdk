package com.klaviyo.pushFcm.carousel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.graphics.scale
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import com.klaviyo.pushFcm.carousel.CarouselConstants.MAX_CAROUSEL_CARDS
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.sqrt

/**
 * Downloads and caches carousel card images.
 *
 * All cards of one carousel are downloaded in parallel at receipt and decoded to a bounded size,
 * so the notification can re-render a card on an arrow tap without another network round trip. The
 * cache is keyed by notification UID and the card's original [CarouselCard.position], and holds at
 * most [MAX_CACHED_CAROUSELS] carousels' worth of bitmaps. Keying by UID rather than tag means a
 * re-send that reuses `notification_tag` never shares, or is overwritten by, the images of the
 * carousel it replaces.
 *
 * Every decoded bitmap is hard-capped at [MAX_BITMAP_BYTES] (about 600 KiB, the worst case being
 * roughly 391×391 ARGB_8888), so one posted notification — which carries only the expanded view's
 * image — stays well under the binder transaction limit.
 *
 * Only [Registry.log] is used here — no [Registry.config] or [Registry.dataStore] access, since
 * both throw before `Klaviyo.initialize` and this runs from FCM receipt and arrow taps alike. Card
 * URLs are never logged: failures report the card position and the URL scheme only.
 */
internal object CarouselImageLoader {
    /**
     * Target card size in pixels, the provisional 2:1 card ratio. The sampler bounds the long edge
     * to [TARGET_WIDTH_PX], and [MAX_BITMAP_BYTES] then caps whatever the power-of-two sample size
     * leaves above budget. PUSH-1934 may change the ratio; PUSH-1939 replaces the decode with a
     * center-crop.
     */
    private const val TARGET_WIDTH_PX = 512
    private const val TARGET_HEIGHT_PX = 256

    /**
     * Hard ceiling on one decoded card's `byteCount`. `inSampleSize` is a power of two, so a
     * near-square source can still land above the target rectangle; anything over this is scaled
     * down after decode. ARGB_8888 is 4 bytes per pixel, so this is about 600 KiB.
     */
    private const val MAX_BITMAP_BYTES = 600 * 1024

    /**
     * Bytes per pixel for the ARGB_8888 bitmaps `BitmapFactory` produces by default.
     */
    private const val BYTES_PER_PIXEL = 4

    /**
     * Number of carousels kept in the cache at once. Two covers the just-shown carousel and one
     * still on screen while the next arrives.
     */
    private const val MAX_CACHED_CAROUSELS = 2

    /**
     * Edge of the collapsed view's square thumbnail in pixels: 48dp at xxxhdpi is 192px, so this
     * stays sharp on every density while costing at most 144 KiB.
     */
    private const val THUMBNAIL_PX = 192

    /**
     * Cache budget in bytes: [MAX_CACHED_CAROUSELS] carousels of [MAX_CAROUSEL_CARDS] cards, each
     * at the [MAX_BITMAP_BYTES] ceiling, plus each carousel's [THUMBNAIL_PX] thumbnail (about
     * 6.1 MiB). Counting the thumbnails keeps a thumbnail put from evicting a card image.
     */
    private const val CACHE_SIZE_BYTES = MAX_CACHED_CAROUSELS *
        (MAX_CAROUSEL_CARDS * MAX_BITMAP_BYTES + THUMBNAIL_PX * THUMBNAIL_PX * BYTES_PER_PIXEL)

    /**
     * Socket connect and read timeouts, split from the shared download budget so one card's
     * worst case (connect then read) still fits inside [KlaviyoNotification.DOWNLOAD_TIMEOUT_MS].
     */
    private const val CONNECT_TIMEOUT_MS = KlaviyoNotification.DOWNLOAD_TIMEOUT_MS / 2
    private const val READ_TIMEOUT_MS = KlaviyoNotification.DOWNLOAD_TIMEOUT_MS / 2

    /**
     * Largest image body accepted, read one chunk at a time so a hostile or broken response cannot
     * exhaust memory before [MAX_BITMAP_BYTES] scaling ever runs.
     */
    private const val MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024

    /**
     * HTTP status codes accepted as an image response.
     */
    private val HTTP_SUCCESS = 200..299

    /**
     * Cache key: the notification UID plus the card's original position, and whether the entry is
     * the card's collapsed-view thumbnail rather than its full image. A data class gives exact
     * [equals]/[hashCode] so [evict] can match one UID without the prefix hazards of a joined string.
     */
    private data class CardKey(
        val uid: String,
        val position: Int,
        val thumbnail: Boolean = false
    )

    private val cache = object : LruCache<CardKey, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: CardKey, value: Bitmap): Int = value.byteCount
    }

    /**
     * Download every card in [cards] that is not already cached, in parallel, waiting at most
     * [KlaviyoNotification.DOWNLOAD_TIMEOUT_MS] in total. One card failing never discards another.
     *
     * @param uid The notification UID identifying the carousel, used as the cache key.
     * @param cards The parsed cards of the carousel.
     * @return Bitmaps that were available (cached or downloaded in time), keyed by card position.
     *  Cards that failed or timed out are simply absent.
     */
    fun loadAll(uid: String, cards: List<CarouselCard>): Map<Int, Bitmap> {
        val results = mutableMapOf<Int, Bitmap>()
        cards.forEach { card -> get(uid, card.position)?.let { results[card.position] = it } }

        val missing = cards.filterNot { results.containsKey(it.position) }
        if (missing.isEmpty()) {
            return results
        }

        val executor = Executors.newFixedThreadPool(missing.size)
        val deadline = System.currentTimeMillis() + KlaviyoNotification.DOWNLOAD_TIMEOUT_MS
        val futures = missing.map { card ->
            card.position to executor.submit<Bitmap?> { download(card) }
        }

        try {
            futures.forEach { (position, future) ->
                // Guard each card independently so one failure or timeout keeps the others.
                try {
                    // Never negative: once the deadline passes, get(0) still returns a card that
                    // finished while an earlier one used up the wait, and times out the rest.
                    val remainingMs = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
                    future.get(remainingMs, TimeUnit.MILLISECONDS)?.let { bitmap ->
                        put(uid, position, bitmap)
                        results[position] = bitmap
                    }
                } catch (_: TimeoutException) {
                    Registry.log.warning(
                        "Carousel card $position image download timed out at " +
                            "${KlaviyoNotification.DOWNLOAD_TIMEOUT_MS}ms; showing available cards"
                    )
                } catch (e: ExecutionException) {
                    // No throwable and no cause message: both can embed the card URL.
                    Registry.log.warning(
                        "Carousel card $position image task failed " +
                            "(${e.cause?.javaClass?.simpleName ?: "unknown"}); skipping image"
                    )
                } catch (e: InterruptedException) {
                    Registry.log.warning(
                        "Carousel card $position image download interrupted; skipping image"
                    )
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        } finally {
            futures.forEach { (_, future) -> future.cancel(true) }
            executor.shutdown()
        }

        return results
    }

    /**
     * Read a cached card image.
     *
     * @param uid The notification UID identifying the carousel.
     * @param position The card's original [CarouselCard.position].
     * @return The cached bitmap, or null when it was never downloaded or has been evicted.
     */
    fun get(uid: String, position: Int): Bitmap? = cache.get(CardKey(uid, position))

    /**
     * The collapsed view's square thumbnail of a card: its cached image, center-cropped to a
     * square and scaled to [THUMBNAIL_PX]. Built once from the full image, then cached under the
     * same UID so [evict] drops it too.
     *
     * @param uid The notification UID identifying the carousel.
     * @param position The card's original [CarouselCard.position].
     * @return The thumbnail, or null when the card's full image is not cached.
     */
    fun thumbnail(uid: String, position: Int): Bitmap? {
        val key = CardKey(uid, position, thumbnail = true)
        cache.get(key)?.let { return it }
        val source = get(uid, position) ?: return null
        return centerCropSquare(source, THUMBNAIL_PX).also { cache.put(key, it) }
    }

    /**
     * Center-crop [source] to a square and scale it to [size]×[size]. The source is never
     * recycled: it is still the card's cached full image.
     */
    internal fun centerCropSquare(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height).coerceAtLeast(1)
        val x = ((source.width - side) / 2).coerceAtLeast(0)
        val y = ((source.height - side) / 2).coerceAtLeast(0)
        val square = Bitmap.createBitmap(source, x, y, side, side)
        val scaled = square.scale(size, size)
        if (scaled !== square && square !== source) {
            square.recycle()
        }
        return scaled
    }

    /**
     * Download a single card and cache it, under the same deadline as [loadAll].
     *
     * @param uid The notification UID identifying the carousel.
     * @param card The card to download.
     * @return The bitmap, or null if the download or decode failed or timed out.
     */
    fun loadOne(uid: String, card: CarouselCard): Bitmap? = loadAll(uid, listOf(card))[card.position]

    /**
     * Drop every cached bitmap for [uid].
     *
     * @param uid The notification UID identifying the carousel.
     */
    fun evict(uid: String) {
        cache.snapshot().keys
            .filter { it.uid == uid }
            .forEach { cache.remove(it) }
    }

    /**
     * Compute the power-of-two [BitmapFactory.Options.inSampleSize] that brings the long edge of a
     * [width]×[height] image to at most the larger of [reqWidth] and [reqHeight].
     *
     * @return 1 for images already within bounds, otherwise the smallest power of two that fits.
     */
    internal fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val targetLongEdge = maxOf(reqWidth, reqHeight)
        val longEdge = maxOf(width, height)
        if (targetLongEdge <= 0 || longEdge <= targetLongEdge) {
            return 1
        }
        var sampleSize = 1
        while (longEdge / sampleSize > targetLongEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Download and decode one card image, logging only the card position and scheme so a full URL
     * (or a throwable message that embeds one) never reaches logs.
     */
    private fun download(card: CarouselCard): Bitmap? {
        val bytes = try {
            readBytes(card)
        } catch (e: Exception) {
            Registry.log.warning(
                "Carousel card ${card.position} image download failed " +
                    "(${e.javaClass.simpleName}, scheme: ${card.imageUrl.protocol}); skipping image"
            )
            return null
        } ?: return null

        return try {
            decode(bytes, card.position)
        } catch (e: Exception) {
            Registry.log.warning(
                "Carousel card ${card.position} image decode failed " +
                    "(${e.javaClass.simpleName}); skipping image"
            )
            null
        }
    }

    /**
     * Read the card's image body under connect/read timeouts, an overall download deadline and the
     * [MAX_DOWNLOAD_BYTES] cap.
     *
     * The per-read socket timeout alone does not bound a trickled response, so the loop also stops
     * once [KlaviyoNotification.DOWNLOAD_TIMEOUT_MS] has elapsed since it started. Together with the
     * connect timeout this keeps the receiver's cache-miss path inside the broadcast budget.
     *
     * @return The bytes, or null when the response is not a 2xx or the body exceeds the cap or the
     *  deadline (already logged).
     */
    private fun readBytes(card: CarouselCard): ByteArray? {
        val connection = card.imageUrl.openConnection().apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        val deadline = System.currentTimeMillis() + KlaviyoNotification.DOWNLOAD_TIMEOUT_MS

        try {
            // An error page (4xx/5xx) or an unfollowed redirect (http -> https) is not an image.
            if (connection is HttpURLConnection && connection.responseCode !in HTTP_SUCCESS) {
                Registry.log.warning(
                    "Carousel card ${card.position} image request returned HTTP " +
                        "${connection.responseCode} (scheme: ${card.imageUrl.protocol}); " +
                        "skipping image"
                )
                return null
            }
            return readBody(card, connection.getInputStream(), deadline)
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    /**
     * Read [stream] to the end under [deadline] and the [MAX_DOWNLOAD_BYTES] cap, closing it.
     */
    private fun readBody(card: CarouselCard, stream: InputStream, deadline: Long): ByteArray? {
        stream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val output = ByteArrayOutputStream()
            while (true) {
                val read = input.read(buffer)
                // A non-positive read is end-of-stream (-1) or a zero-length read, which must not
                // spin the loop; either way the body is complete.
                if (read <= 0) {
                    break
                }
                if (output.size() + read > MAX_DOWNLOAD_BYTES) {
                    Registry.log.warning(
                        "Carousel card ${card.position} image exceeds " +
                            "${MAX_DOWNLOAD_BYTES / (1024 * 1024)}MB " +
                            "(scheme: ${card.imageUrl.protocol}); skipping image"
                    )
                    return null
                }
                if (System.currentTimeMillis() > deadline) {
                    Registry.log.warning(
                        "Carousel card ${card.position} image download exceeded " +
                            "${KlaviyoNotification.DOWNLOAD_TIMEOUT_MS}ms " +
                            "(scheme: ${card.imageUrl.protocol}); skipping image"
                    )
                    return null
                }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    /**
     * Decode card bytes at a bounded sample size, then scale down further if the result still
     * exceeds [MAX_BITMAP_BYTES].
     */
    private fun decode(bytes: ByteArray, position: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Registry.log.warning("Carousel card $position image could not be decoded")
            return null
        }
        val sampleSize = calculateInSampleSize(
            bounds.outWidth,
            bounds.outHeight,
            TARGET_WIDTH_PX,
            TARGET_HEIGHT_PX
        )
        // PUSH-1939: replace this plain sample decode with a center-crop to the final card ratio.
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null

        return capToBudget(decoded, position)
    }

    /**
     * Scale [bitmap] down proportionally until its `byteCount` fits [MAX_BITMAP_BYTES].
     *
     * `inSampleSize` is a power of two, so a near-square source can decode above the target
     * rectangle. Scaling is a no-op for the common case, which is already under budget.
     */
    private fun capToBudget(bitmap: Bitmap, position: Int): Bitmap {
        if (bitmap.byteCount <= MAX_BITMAP_BYTES) {
            return bitmap
        }
        // Read the bytes per pixel off the bitmap: RGBA_F16 is 8, so assuming ARGB_8888's 4 would
        // leave it at twice the budget.
        val pixels = (bitmap.width * bitmap.height).coerceAtLeast(1)
        val bytesPerPixel = (bitmap.byteCount / pixels).coerceAtLeast(1)
        val maxPixels = MAX_BITMAP_BYTES / bytesPerPixel
        val scale = sqrt(
            maxPixels.toDouble() / (bitmap.width.toDouble() * bitmap.height)
        )
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = bitmap.scale(width, height)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        Registry.log.verbose(
            "Scaled carousel card $position image to ${scaled.width}×${scaled.height} " +
                "(${scaled.byteCount} bytes)"
        )
        return scaled
    }

    /**
     * Insert into the cache; the LruCache evicts least-recently-used entries past the budget.
     */
    private fun put(uid: String, position: Int, bitmap: Bitmap) {
        cache.put(CardKey(uid, position), bitmap)
    }
}
