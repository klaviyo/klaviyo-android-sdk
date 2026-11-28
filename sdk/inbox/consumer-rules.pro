# Keep only class names for stack traces, allow member obfuscation
-keepnames class com.klaviyo.inbox.**

# Keep ContentProvider for auto-registration
-keep class com.klaviyo.inbox.InboxInitProvider
