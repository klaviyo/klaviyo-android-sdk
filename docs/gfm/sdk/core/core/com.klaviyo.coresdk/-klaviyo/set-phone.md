//[core](../../../index.md)/[com.klaviyo.coresdk](../index.md)/[Klaviyo](index.md)/[setPhone](set-phone.md)

# setPhone

[androidJvm]\
fun [setPhone](set-phone.md)(phone: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Klaviyo](index.md)

Assigns a phone number to the current UserInfo

UserInfo is saved to keep track of current profile details and used to autocomplete analytics requests with profile identifier when not specified

This should be called whenever the active user in your app changes (e.g. after a fresh login)

#### Parameters

androidJvm

| | |
|---|---|
| phone | Phone number for active user |
