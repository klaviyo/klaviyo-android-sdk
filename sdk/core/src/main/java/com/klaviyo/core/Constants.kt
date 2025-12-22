package com.klaviyo.core

/**
 * Compile-time constants shared across SDK modules
 */
object Constants {
    /**
     * Package prefix used for Klaviyo intent extras and data keys
     */
    const val PACKAGE_PREFIX = "com.klaviyo."

    /**
     * Key-value pairs get special treatment in a few places across multiple packages
     */
    const val KEY_VALUE_PAIRS = "key_value_pairs"

    /**
     * Klaviyo push messages contain metadata to associate an event with its original transmission
     */
    const val TRACKING_PARAMETER = "_k"
}
