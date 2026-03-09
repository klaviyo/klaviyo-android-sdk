# Keep only class names for stack traces, allow member obfuscation
-keepnames class com.klaviyo.location.**

# Keep the ContentProvider for auto-registration
-keep class com.klaviyo.location.LocationInitProvider
