package com.klaviyo.location

typealias PermissionObserver = (Boolean) -> Unit

interface PermissionMonitor {
    val permissionState: Boolean
    fun onPermissionChanged(callback: PermissionObserver)
    fun offPermissionChanged(callback: PermissionObserver)
}
