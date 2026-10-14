# Keep only class names for stack traces, allow member obfuscation
-keepnames class com.klaviyo.analytics.**
# Keep all model classes, it is not uncommon to reference these dynamically (especially keywords)
-keep class com.klaviyo.analytics.model.** { *; }
# Host apps implement the auth token provider, so keep its members stable
-keep interface com.klaviyo.analytics.AuthTokenProvider { *; }
-keep interface com.klaviyo.analytics.AuthTokenProvider$Callback { *; }
