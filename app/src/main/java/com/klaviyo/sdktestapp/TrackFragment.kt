package com.klaviyo.sdktestapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.track_pane.*
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.TrackRequest

class TrackFragment: AnalyticsFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.track_pane, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sendButton.setOnClickListener(sendClickListener)
    }

    private val sendClickListener = View.OnClickListener {
        KlaviyoConfig.Builder()
            .apiKey("LuYLmF")
            .applicationContext(TestApp.applicationContext)
            .build()
        //TODO: We should build these requests with fields for a user to fill in so that we can customize the payloads we send
        val request = TrackRequest(
            "Test Event",
            hashMapOf("\$email" to "sdktest@test.com")
            )
        request.generateUnixTimestamp()
        Thread {
            val success = request.process()
            Log.d("Track successful", success.toString())
        }.start()
    }
}