package com.klaviyo.sdktestapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.networking.KlaviyoEvent
import kotlinx.android.synthetic.main.track_pane.*

class TrackFragment: AnalyticsFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.track_pane, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sendButton.setOnClickListener(sendClickListener)
    }

    //TODO: We should build these requests with fields for a user to fill in so that we can customize the payloads we send
    private val sendClickListener = View.OnClickListener {
        Klaviyo.track(KlaviyoEvent.CUSTOM_EVENT("test_event"), hashMapOf("\$email" to "sdktest@test.com"))
    }
}