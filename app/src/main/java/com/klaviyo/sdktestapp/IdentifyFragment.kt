package com.klaviyo.sdktestapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.identify_pane.*
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.IdentifyRequest


class IdentifyFragment: AnalyticsFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.identify_pane, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sendButton.setOnClickListener(sendClickListener)
    }

    private val sendClickListener = View.OnClickListener {
        //TODO: Move the config builder stuff here (and in Track request) to some common setup area
        KlaviyoConfig.Builder()
            .apiKey("LuYLmF")
            .applicationContext(TestApp.applicationContext)
            .build()
        val request = IdentifyRequest(
            hashMapOf("\$email" to "sdktest@test.com", "\$value" to "100")
        )
        Thread {
            val success = request.process()
            Log.d("Identify successful", success.toString())
        }.start()
    }
}