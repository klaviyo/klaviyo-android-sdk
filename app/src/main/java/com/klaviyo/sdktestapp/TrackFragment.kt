package com.klaviyo.sdktestapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
import com.klaviyo.coresdk.networking.KlaviyoEventPropertyKeys
import com.klaviyo.sdktestapp.databinding.TrackPaneBinding

class TrackFragment : AnalyticsFragment<TrackPaneBinding>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = TrackPaneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            sendButton.setOnClickListener(sendClickListener)
        }
    }

    // TODO: We should build these requests with fields for a user to fill in so that we can customize the payloads we send
    private val sendClickListener = View.OnClickListener {
        val customerProperties = KlaviyoCustomerProperties()
            .also {
                it.setEmail("sdktest@test.com")
            }
        val properties = KlaviyoEventProperties().also {
            it.addValue("100")
            it.addProperty(
                KlaviyoEventPropertyKeys.CUSTOM("currency"), "USD"
            )
        }
        Klaviyo.track(KlaviyoEvent.CUSTOM_EVENT("test_event"), customerProperties, properties)
    }
}
