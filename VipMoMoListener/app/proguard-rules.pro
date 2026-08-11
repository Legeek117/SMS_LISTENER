# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools/proguard/proguard-android.txt

# Keep Room entities
-keep class com.cryptovip.smslistener.SmsLog { *; }
-keep class com.cryptovip.smslistener.PendingSms { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
