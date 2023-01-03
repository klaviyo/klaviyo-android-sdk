//[core](../../../index.md)/[com.klaviyo.coresdk](../index.md)/[Klaviyo](index.md)/[configure](configure.md)

# configure

[androidJvm]\
fun [configure](configure.md)(apiKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), applicationContext: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), networkTimeout: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = KlaviyoConfig.NETWORK_TIMEOUT_DEFAULT, networkFlushInterval: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = KlaviyoConfig.NETWORK_FLUSH_INTERVAL_DEFAULT, networkFlushDepth: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = KlaviyoConfig.NETWORK_FLUSH_DEPTH_DEFAULT, networkFlushCheckInterval: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = KlaviyoConfig.NETWORK_FLUSH_CHECK_INTERVAL, networkUseAnalyticsBatchQueue: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = KlaviyoConfig.NETWORK_USE_ANALYTICS_BATCH_QUEUE): [Klaviyo](index.md)

Configure Klaviyo SDK with your account's public API Key and application context. Optionally specify additional behavior customization

This must be called to initialize the SDK before using any other functionality

#### Return

#### Parameters

androidJvm

| | |
|---|---|
| apiKey | -     Your Klaviyo account's public API Key |
| applicationContext |
| networkTimeout |
| networkFlushInterval |
| networkFlushDepth |
| networkFlushCheckInterval |
| networkUseAnalyticsBatchQueue |
