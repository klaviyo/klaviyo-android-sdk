# Keep only class names for stack traces, allow member obfuscation
-keepnames class com.klaviyo.analytics.**
# Keep all model classes, it is not uncommon to reference these dynamically (especially keywords)
-keep class com.klaviyo.analytics.model.** { *; }
