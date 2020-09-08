package com.klaviyo.sdktestapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.klaviyo.coresdk.Klaviyo
import kotlinx.android.synthetic.main.identify_pane.sendButton


class IdentifyFragment: AnalyticsFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.identify_pane, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sendButton.setOnClickListener(sendClickListener)

    }

    private val sendClickListener = View.OnClickListener {
        Klaviyo.identify(hashMapOf("\$email" to "sdktest@test.com", "\$value" to "100"))
    }
}