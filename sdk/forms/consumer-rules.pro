# Keep only class names for stack traces, allow member obfuscation
-keepnames class com.klaviyo.forms.**

# Keep ContentProvider for auto-registration
-keep class com.klaviyo.forms.FormsInitProvider

# Keep all methods annotated with @JavascriptInterface for WebView bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
