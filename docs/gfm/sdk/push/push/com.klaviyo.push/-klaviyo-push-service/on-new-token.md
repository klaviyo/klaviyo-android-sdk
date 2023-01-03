//[push](../../../index.md)/[com.klaviyo.push](../index.md)/[KlaviyoPushService](index.md)/[onNewToken](on-new-token.md)

# onNewToken

[androidJvm]\
open override fun [onNewToken](on-new-token.md)(newToken: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

FCM service calls this function whenever a token is generated This can be whenever a token is created anew, or whenever it has expired and regenerated itself

Invoke the SDK to log the push notification to the profile

#### Parameters

androidJvm

| | |
|---|---|
| newToken | The newly generated token returned from the FCM service |
