//[push](../../../../index.md)/[com.klaviyo.push](../../index.md)/[KlaviyoPushService](../index.md)/[Companion](index.md)/[setPushToken](set-push-token.md)

# setPushToken

[androidJvm]\
fun [setPushToken](set-push-token.md)(pushToken: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))

Save the device FCM push token and register to the current profile

We append this token to a property map and queue it into an identify request to send to the Klaviyo asynchronous APIs. We then write it into the shared preferences so that we can fetch the token for this device as needed

#### See also

androidJvm

| | |
|---|---|
| FirebaseMessagingService.onNewToken | () |

#### Parameters

androidJvm

| | |
|---|---|
| pushToken | The push token provided by the FCM Service |
