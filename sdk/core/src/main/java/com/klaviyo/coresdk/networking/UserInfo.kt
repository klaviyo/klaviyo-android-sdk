package com.klaviyo.coresdk.networking

//TODO: Eventually we want to build this up into a user session
// but for now we just need emails on initialization to associate push tokens with accounts
internal object UserInfo {
    var email: String = ""

    fun hasEmail(): Boolean {
        return email.isNotEmpty()
    }
}