package com.klaviyo.pushFcm.carousel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.klaviyo.core.Constants.MAX_CAROUSEL_CARDS
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.CarouselCard
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Downloads and caches carousel card images.
 *
 * All cards of one carousel are downloaded in parallel at receipt and decoded to a bounded sample
 * size, so the notification can re-render a card on an arrow tap without another network round
 * trip. The cache is keyed by notification tag and the card's original [CarouselCard.position],
 * and holds at most [MAX_CACHED_CAROUSELS] carousels' worth of bitmaps.
 *
 * This lives outside the notification: only the expanded `RemoteViews` carries a bitmap, so one
 * posted notification holds one card image (about 0.5 MB) and stays well under the binder limit.
 * Only [Registry.log] is used here — no [Registry.config] or [Registry.dataStore] access, since
 * both throw before `Klaviyo.initialize` and this runs from FCM receipt and arrow taps alike.
 */
internal object CarouselImageLoader {
    /**
     * Long edge, in pixels, that a decoded card image is sampled down to. With the provisional 2:1
     * card ratio this caps a card at roughly 512×256 ARGB_8888, about 0.5 MB.
     */
    private const val TARGET_MAX_EDGE_PX = 512

    /**
     * Number of carousels kept in the cache at once. Two covers the just-shown carousel and one
     * still on screen while the next arrives.
     */
    private const val MAX_CACHED_CAROUSELS = 2

    /**
     * Cache budget in bytes: two carousels of [MAX_CAROUSEL_CARDS] cards at about 0.5 MB each.
     */
    private const val CACHE_SIZE_BYTES = 5 * 1024 * 1024

    /**
     * Cache key: the notification tag plus the card's original position. A data class gives exact
     * [equals]/[hashCode] so [evict] can match one tag without the prefix hazards of a joined string.
     */
    private data class CardKey(val tag: String, val position: Int)

    private val cache = object : LruCache<CardKey, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: CardKey, value: Bitmap): Int = value.byteCount
    }

    /**
     * Download every card in [cards] that is not already cached, in parallel, waiting at most
     * [KlaviyoNotification.DOWNLOAD_TIMEOUT_MS] in total.
     *
     * @param tag The notification tag identifying the carousel, used as the cache key prefix.
     * @param cards The parsed cards of the carousel.
     * @return Bitmaps that were available (cached or downloaded in time), keyed by card position.
     *  Cards that failed or timed out are simply absent.
     */
    fun loadAll(tag: String, cards: List<CarouselCard>): Map<Int, Bitmap> {
        val results = mutableMapOf<Int, Bitmap>()
        cards.forEach { card -> get(tag, card.position)?.let { results[card.position] = it } }

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
                val remainingMs = deadline - System.currentTimeMillis()
                if (remainingMs <= 0) {
                    throw TimeoutException()
                }
                future.get(remainingMs, TimeUnit.MILLISECONDS)?.let { bitmap ->
                    put(tag, position, bitmap)
                    results[position] = bitmap
                }
            }
        } catch (_: TimeoutException) {
            Registry.log.warning(
                "Carousel image download timed out at " +
                    "${KlaviyoNotification.DOWNLOAD_TIMEOUT_MS}ms; showing available cards"
            )
        } catch (e: ExecutionException) {
            Registry.log.warning("Carousel image download failed: ${e.cause}", e)
        } catch (e: InterruptedException) {
            Registry.log.warning("Carousel image download interrupted: ${e.cause}", e)
            Thread.currentThread().interrupt()
        } finally {
            futures.forEach { (_, future) -> future.cancel(true) }
            executor.shutdown()
        }

        return results
    }

    /**
     * Read a cached card image.
     *
     * @param tag The notification tag identifying the carousel.
     * @param position The card's original [CarouselCard.position].
     * @return The cached bitmap, or null when it was never downloaded or has been evicted.
     */
    fun get(tag: String, position: Int): Bitmap? = cache.get(CardKey(tag, position))

    /**
     * Download a single card and cache it. For the receiver's cache-miss path after process death.
     *
     * @param tag The notification tag identifying the carousel.
     * @param card The card to download.
     * @return The bitmap, or null if the download or decode failed.
     */
    fun loadOne(tag: String, card: CarouselCard): Bitmap? =
        get(tag, card.position) ?: download(card)?.also { put(tag, card.position, it) }

    /**
     * Drop every cached bitmap for [tag].
     *
     * @param tag The notification tag identifying the carousel.
     */
    fun evict(tag: String) {
        cache.snapshot().keys
            .filter { it.tag == tag }
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
     * Download and decode one card image, logging only the scheme so a full URL never reaches logs.
     */
    private fun download(card: CarouselCard): Bitmap? {
        val bytes = try {
            card.imageUrl.openStream().use { it.readBytes() }
        } catch (e: Exception) {
            Registry.log.warning(
                "Carousel card ${card.position} image download failed (scheme: " +
                    "${card.imageUrl.protocol}); skipping image",
                e
            )
            return null
        }
        return decode(bytes, card.position)
    }

    /**
     * Decode card bytes at a bounded sample size.
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
            TARGET_MAX_EDGE_PX,
            TARGET_MAX_EDGE_PX
        )
        // PUSH-1939: replace this plain sample decode with a center-crop to the final card ratio.
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    }

    /**
     * Insert into the cache; the LruCache evicts least-recently-used entries past the budget.
     */
    private fun put(tag: String, position: Int, bitmap: Bitmap) {
        cache.put(CardKey(tag, position), bitmap)
    }
}
