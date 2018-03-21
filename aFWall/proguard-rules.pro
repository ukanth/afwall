-keepattributes **
-keep class org.ocpsoft.prettytime.i18n.**
-keep class !android.support.v7.view.menu.**,!android.support.design.internal.NavigationMenu,!android.support.design.internal.NavigationMenuPresenter,!android.support.design.internal.NavigationSubMenu,** {*;}
-keep class * extends com.raizlabs.android.dbflow.config.DatabaseHolder { *; }
-dontpreverify
-dontoptimize
-dontshrink
-dontobfuscate

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

-keep public class android.support.v7.widget.** { *; }
-keep public class android.support.v7.internal.widget.** { *; }
-keep public class android.support.v7.internal.view.menu.** { *; }

-keep public class * extends android.support.v4.view.ActionProvider {
    public <init>(android.content.Context);
}

-optimizations !code/allocation/variable
