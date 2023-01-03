//[core](../../../index.md)/[com.klaviyo.coresdk](../index.md)/[Klaviyo](index.md)/[setEmail](set-email.md)

# setEmail

[androidJvm]\
fun [setEmail](set-email.md)(email: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Klaviyo](index.md)

Assigns an email address to the current UserInfo

UserInfo is saved to keep track of current profile details and used to autocomplete analytics requests with profile identifier when not specified

This should be called whenever the active user in your app changes (e.g. after a fresh login)

#### Parameters

androidJvm

| | |
|---|---|
| email | Email address for active user |
