package com.klaviyo.sdktestapp

import android.os.Bundle
import kotlinx.android.synthetic.main.activity_analytics.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.klaviyo.push.KlaviyoPushService
import java.util.*

class AnalyticsActivity: FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        val pagerAdapter = AnalyticsPagerAdapter(this)
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

    private inner class AnalyticsPagerAdapter(activity: FragmentActivity): FragmentStateAdapter(activity) {
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