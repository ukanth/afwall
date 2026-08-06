-keepattributes
-keep class org.ocpsoft.prettytime.i18n.**
-keep class * extends com.raizlabs.android.dbflow.config.DatabaseHolder { *; }
-dontpreverify
-dontoptimize
-dontobfuscate
-keep class dev.ukanth.ufirewall.** { *; }
-optimizations !code/allocation/variable

# Android 16 specific proguard rules
-keep class android.window.** { *; }
-keep class androidx.activity.** { *; }
-dontwarn android.window.**
-dontwarn androidx.window.**

# Edge-to-edge and window insets support
-keep class androidx.core.view.WindowInsetsCompat** { *; }
-keep class androidx.core.view.ViewCompat** { *; }

# Notification channel compatibility
-keep class androidx.core.app.NotificationChannelCompat** { *; }
