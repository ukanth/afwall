-keepattributes **
-keep class org.ocpsoft.prettytime.i18n.**
-keep class * extends com.raizlabs.android.dbflow.config.DatabaseHolder { *; }
-dontpreverify
-dontoptimize
-dontobfuscate
-ignorewarning

#-keepattributes *Annotation*
#-keepclassmembers class ** {
#    @org.greenrobot.eventbus.Subscribe <methods>;
#}

#-keepattributes *Annotation*
#-keepclassmembers class ** {
#    @com.squareup.otto.Subscribe public *;
#    @com.squareup.otto.Produce public *;
#}

-dontnote org.xbill.DNS.spi.DNSJavaNameServiceDescriptor
-dontwarn org.xbill.DNS.spi.DNSJavaNameServiceDescriptor

-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Only required if you use AsyncExecutor
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}

-keep class dev.ukanth.ufirewall.** { *; }

-optimizations !code/allocation/variable
