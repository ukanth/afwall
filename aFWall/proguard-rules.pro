-keepattributes **
# Allow obfuscation of android.support.v7.internal.view.menu.**
# to avoid problem on Samsung 4.2.2 devices with appcompat v21
# see https://code.google.com/p/android/issues/detail?id=78377
# Fix bug on Samsung, Wiko (and other) devices running Android 4.2
# See also: https://code.google.com/p/android/issues/detail?id=78377
-keep class !android.support.v7.view.menu.**,!android.support.design.internal.NavigationMenu,!android.support.design.internal.NavigationMenuPresenter,!android.support.design.internal.NavigationSubMenu,** {*;}
-keep class * extends com.raizlabs.android.dbflow.config.DatabaseHolder { *; }
-dontpreverify
-dontoptimize
-dontshrink


