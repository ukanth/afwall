# R8 rules for release builds (shrink + obfuscate). Upload app/build/outputs/mapping/release/mapping.txt
# to Play Console with each release to read crash stack traces.

# readable stack traces once retraced with mapping.txt
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Api.installBinaryIfExists() looks up R.raw fields by name (busybox_*, iptables_*, nflog_*)
-keep class dev.ukanth.ufirewall.R$raw { *; }

# preference headers (res/xml/preferences_headers.xml) name these fragments by class
-keep public class * extends android.preference.PreferenceFragment
-keep public class * extends androidx.preference.PreferenceFragmentCompat

# DBFlow loads the generated holder by name (FlowManager)
-keep class * extends com.raizlabs.android.dbflow.config.DatabaseHolder { *; }
-keep class com.raizlabs.android.dbflow.config.GeneratedDatabaseHolder { *; }

# PrettyTime loads its translations by class name
-keep class org.ocpsoft.prettytime.i18n.** { *; }

# dnsjava: optional dependencies that are not on Android
-dontwarn javax.naming.**
-dontwarn lombok.**
-dontwarn org.slf4j.**
-dontwarn sun.net.spi.nameservice.**
-dontwarn com.sun.jna.**
# Java 18+ InetAddress resolver SPI; not on Android, never loaded there
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider
