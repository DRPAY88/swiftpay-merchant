# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\densh\AppData\Local\Android\Sdk/tools/proguard/proguard-android-optimize.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep API models and DTOs
-keep class com.example.myapplication.data.api.** { *; }
-keep class com.example.myapplication.bridge.** { *; }

# Keep JavaScript Interfaces for Web View SDK Bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Attributes for Reflection / Serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-dontwarn okio.**
-dontwarn javax.annotation.**
-dontoptimize

