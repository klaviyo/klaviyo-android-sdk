package com.klaviyo.sdktestapp

import androidx.fragment.app.Fragment

abstract class AnalyticsFragment<ViewBinding> : Fragment() {

    protected var viewBinding: ViewBinding? = null
    protected val binding get() = viewBinding!!

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }
}
