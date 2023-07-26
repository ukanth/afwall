-keepattributes
-dontpreverify
-dontoptimize
-dontobfuscate
-optimizations !code/allocation/variable

-keep class org.ocpsoft.prettytime.i18n.**
-keep class * extends com.raizlabs.android.dbflow.config.DatabaseHolder { *; }
-keep class dev.ukanth.ufirewall.** { *; }
