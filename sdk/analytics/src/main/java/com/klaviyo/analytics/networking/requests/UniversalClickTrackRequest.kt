package com.klaviyo.analytics.networking.requests

import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds
import org.json.JSONObject

/**
 * Makes an HTTP request to a klaviyo click tracking */
internal class UniversalClickTrackRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest("", RequestMethod.GET, queuedTime, uuid) {

    companion object {
        const val KLAVIYO_PROFILE_INFO_HEADER = "X-Klaviyo-Profile-Info"
        const val KLAVIYO_CLICK_TIMESTAMP_HEADER = "X-Klaviyo-Click-Event-Timestamp"
        const val DESTINATION_URL_KEY = "original_destination"
    }

    override val type: String = "Universal Link"

    /**
     * Use [baseUrl] to capture whole tracking URL on this request type
     */
    override var baseUrl: String = ""

    /**
     * Extract the destination URL from the response JSON
     * This could be null if the request hasn't completed yet or if the parsing fails
     */
    private val destinationUrl: URL?
        get() = try {
            responseBody?.let { body ->
                JSONObject(body).optString(DESTINATION_URL_KEY)
            }?.takeIf {
                it.isNotEmpty()
            }?.let {
                URL(it)
            }
        } catch (e: Exception) {
            Registry.log.warning("Failed to parse destination URL", e)
            null
        }

    /**
     * Primary constructor for creating the request with a tracking URL and profile
     */
    constructor(trackingUrl: String, profile: Profile) : this() {
        this.baseUrl = trackingUrl
        headers.put(KLAVIYO_PROFILE_INFO_HEADER, profile.identifiers.toString())
    }

    /**
     * Get the result of the request as a [ResolveDestinationResult]
     */
    fun getResult(): ResolveDestinationResult = when (status) {
        Status.Complete -> destinationUrl?.let { destinationUrl ->
            ResolveDestinationResult.Success(destinationUrl)
        } ?: ResolveDestinationResult.Failure

        Status.Unsent, Status.Inflight -> ResolveDestinationResult.Unavailable

        else -> ResolveDestinationResult.Failure
    }.also { result ->
        if (result is ResolveDestinationResult.Success) {
            Registry.log.verbose("Resolved destination URL: ${result.destinationUrl}")
        } else {
            Registry.log.warning("Failed to resolve destination URL for $baseUrl.")
        }
    }

    /**
     * If the initial request failed, update request format to go into the queue to be retried later
     */
    fun prepareToEnqueue() = apply {
        headers[KLAVIYO_CLICK_TIMESTAMP_HEADER] = queuedTime.milliseconds.inWholeSeconds.toString()
        attempts = 0
    }
}
