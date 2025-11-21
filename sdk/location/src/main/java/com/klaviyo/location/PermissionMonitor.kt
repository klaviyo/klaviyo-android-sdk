package com.klaviyo.location

typealias PermissionObserver = (Boolean) -> Unit

interface PermissionMonitor {
    val permissionState: Boolean

    /**
     * Register an observer to be notified when permission state changes
     *
     * @param unique If true, prevents registering the same observer multiple times.
     *               Note this only works for references e.g. ::method, not lambdas
     * @param callback The observer function to be called when permission state changes
     */
    fun onPermissionChanged(unique: Boolean = false, callback: PermissionObserver)

    /**
     * Unregister an observer previously added with [onPermissionChanged]
     */
    fun offPermissionChanged(callback: PermissionObserver)
}
