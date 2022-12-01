package com.klaviyo.sdktestapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.klaviyo.push.KlaviyoPushService
import com.klaviyo.sdktestapp.databinding.ActivityAnalyticsBinding
import java.util.LinkedList

class AnalyticsActivity : FragmentActivity() {

    private lateinit var viewBinding: ActivityAnalyticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        setupView()
    }

    private fun setupView() {
        viewBinding.apply {
            val pagerAdapter = AnalyticsPagerAdapter(this@AnalyticsActivity)
            pagerAdapter.addFragment(TrackFragment())
            pagerAdapter.addFragment(IdentifyFragment())

            analyticsPager.adapter = pagerAdapter

            TabLayoutMediator(analyticsTabs, analyticsPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "Track Request"
                    1 -> "Identify Request"
                    else -> null
                }
            }.attach()

            pushTokenTextView.text = KlaviyoPushService.getCurrentPushToken()
        }
    }

    private inner class AnalyticsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        private val fragments = LinkedList<Fragment>()

        fun addFragment(fragment: Fragment) {
            fragments.add(fragment)
        }

        override fun getItemCount(): Int {
            return fragments.size
        }

        override fun createFragment(position: Int): Fragment {
            return fragments[position]
        }
    }
}
