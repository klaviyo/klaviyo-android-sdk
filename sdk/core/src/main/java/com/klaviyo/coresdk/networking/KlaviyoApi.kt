package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.requests.NetworkRequest
import java.util.concurrent.ConcurrentLinkedQueue

// TODO I'd envision this being a simple interface for enqueuing API requests of any kind
//  that internally handles flushing the queues (which is mostly written in NetworkBatcher already)
//  as well as writing queues to persistent store and restoring queue from disk.
/**
 * Internal coordinator of API traffic
 */
internal class KlaviyoApi {
    private var queue = ConcurrentLinkedQueue<NetworkRequest>()

    fun enqueueProfileCall(profile: Profile) {
        // TODO enqueue a profile api call
    }

    fun enqueueEventCall(event: Event) {
        // TODO enqueue an event api call
    }
}
