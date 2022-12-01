package com.klaviyo.sdktestapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.sdktestapp.databinding.IdentifyPaneBinding

class IdentifyFragment : AnalyticsFragment<IdentifyPaneBinding>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = IdentifyPaneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            sendButton.setOnClickListener(sendClickListener)
        }
    }

    private val sendClickListener = View.OnClickListener {
        val properties = KlaviyoCustomerProperties()
            .also {
                it.setEmail("sdktest@test.com")
            }
        Klaviyo.identify(properties = properties)
    }
}
