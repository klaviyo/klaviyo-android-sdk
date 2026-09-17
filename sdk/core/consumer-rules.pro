# Keep only class names for stack traces, allow member obfuscation
-keepnames class com.klaviyo.core.**

# Keep ContentProvider for auto-registration
-keep class com.klaviyo.core.CoreInitProvider
