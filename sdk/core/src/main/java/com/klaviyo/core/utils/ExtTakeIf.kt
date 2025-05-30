package com.klaviyo.core.utils

/**
 * Extension function to cast an object to a specific type if it matches the generic type.
 * If the object is null or does not match the type, it returns null.
 */
inline fun <reified T : Any> Any?.takeIf() = let { it as? T }

/**
 * Extension of takeIf to exclude with a generic type parameter.
 */
inline fun <SuperType, reified SubType : SuperType> SuperType?.takeIfNot() =
    takeIf { it !is SubType }
