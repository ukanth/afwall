## AFWall+ (Android Firewall+) [![Build Status](https://travis-ci.org/ukanth/afwall.png?branch=beta)](https://travis-ci.org/ukanth/afwall) [![Crowdin](https://d322cqt584bo4o.cloudfront.net/afwall/localized.png)](https://crowdin.net/project/afwall)
[![Donate](https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=6E4VZTULRB8GU)
======

![AFwall+](https://raw.githubusercontent.com/ukanth/afwall/0502e6f17ceda08069720ff2f260902690e65e9b/screenshots/Main_2.0.png)

[![Google Play](http://developer.android.com/images/brand/en_generic_rgb_wo_45.png)](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall)

Index
-----

* [Description](#description)
* [Features](#features)
* [Changelog](https://github.com/ukanth/afwall/blob/beta/Changelog.md)
* [Bug Reports](#bug-reports)
* [Limitations](#limitations)
* [Compatibility](#compatibility)
* [Upgrading](#upgrading)
* [Permissions](#permissions)
* [Frequently asked questions](#frequently-asked-questions)
* [License](#license)
* [Acknowledgements](#acknowledgements)
* [Compile AFWall+](#compile-afwall)
* [Compile Native Binaries](#compiling-native-binaries)
* [Contributing](#contributing)
* [Translating](#translating)

Description
-----------

Android Firewall+ is an advanced iptables editor (GUI) for Android. It provides fine-grained control over which Android apps are allowed to access the network.

The original codebase was derived from [DroidWall](http://code.google.com/p/droidwall) by Rodrigo Rosauro. DroidWall was sold to AVAST in December 2011, and is no longer actively maintained.

For more information and community discussions, please visit the [XDA thread](http://forum.xda-developers.com/showthread.php?t=1957231) or [our Wiki](https://github.com/ukanth/afwall/wiki).

Features
--------
* Support 4.x/5.x and 6.x versions of Android
* Easy to install & simple to use
* Free and open source
* No advertisements
* Choose your preferred language
* Search for installed applications
* Sort installed applications by installed date/uid/alphabatical order
* Get a notification about new installed application with internet permission
* Blocked packets notification and filter
* Password protection
* Device Admin Support (Protect AFWall+ from uninstall)
* Easy to manage your rules with a custom script
* Ipv4/Ipv6 support
* LAN-, VPN-, Tether-, Roaming-Control support
* Tasker and Locale support
* Firewall Logs service
* Multi-User (multiple-profiles) support
* Build-In Iptables/Busybox
* Export and Import rules (Import All Rules needs donate version)
* Option to prevent data leaks during boot (REQUIRES init.d support or S-OFF)
* Supports ARM/MIPS/x86

Bug Reports
--------

Please see the [issues](https://github.com/ukanth/afwall/issues) section to
report any bugs or feature requests and to see the list of known issues. Before you report a bug, take a look [here](https://github.com/ukanth/afwall/wiki/HOWTO-Report-Bug).  

Limitations
-----------

* A firewall cannot protect against attacks that are performed outside the operating point. For example, if there is a Point-TO-POINT to the Internet.
* The firewall cannot prevent corporate data from being copied to a memory stick or HDD and these are subtracted from the building. 
* AFWall+ cannot have a precise system of SCAN for each type of virus/malware that may arise in the files that pass through it, because the Firewall is not an Antivirus solution.

Compatibility
-------------

AFWall+ has been success tested with Android version 4.xx - 5. (ICS, JellyBean, KitKat, Lollipop) and is reported to work with most Android variants, including stock ROMs

I do not recommend using AFWall+ in combination with any of the similar solutions because this could result in conflicts and potential data leaks (iptables could be overwritten)

Upgrading
---------

* **Make a backup** (e.g. with Titanium Backup)
* **Do not remove the previous version** (or else your settings will maybe get lost)
* Download the new version
* Install the new version over the previous version
* Done

Permissions
-----------

AFWall+ asks for the following Android permissions:

* RECEIVE_BOOT_COMPLETED: Autostart (Bootup) AFWall+ after the system finishes booting.
* ACCESS_NETWORK_STATE: Allows AFWall+ to access information about networks (iptables).
* WRITE_EXTERNAL_STORAGE: Allows AFWall+ to write to external storage for debug log and export iptables rules.
* ACCESS_SUPERUSER: Standard to support Superuser/SuperSU (by Koushik/Chainfire).
* INTERNET : NetworkInterface.getNetworkInterfaces() needs android.permission.INTERNET. This is just being used to get the IPv4 and IPv6 addresses/subnets for each interface, so the LAN address ranges can be determined. Nothing is actually trying to access the network. Also take a look at [Stackoverflow](http://stackoverflow.com/questions/17252018/getting-my-lan-ip-address-192-168-xxxx-ipv4).
* ACCESS_WIFI_STATE : Added to detect tether state.

Frequently asked questions
--------------------------

Having problems with AFWall+? Check out the [FAQ](https://github.com/ukanth/afwall/wiki/FAQ) before reporting a bug or problem that may already be known.

License
-------

AFWall+ is under the [GNU General Public License v3.0 License](https://www.gnu.org/licenses/gpl.html).

Acknowledgements
----------------

This project also uses many other open source libraries such as:

<table>
    <tr>
        <td><strong>Project</strong></td>
        <td><strong>License</strong></td>
        <td><strong>Website</strong></td>
    </tr>
    <tr>
        <td>Android Color Picker</td>
        <td>Apache License 2.0</td>
        <td>https://github.com/attenzione/android-ColorPickerPreference</td>
    </tr>
	<tr>
        <td>Busybox</td>
        <td>GNU GPLv2</td>
        <td>http://www.busybox.net</td>
    </tr>
    <tr>
        <td>external_klogripper</td>
        <td>GNU General Public License 2.0</td>
        <td>https://github.com/VanirAOSP/external_klogripper</td>
    </tr>
	<tr>
        <td>iptables</td>
        <td>GNU GPLv2</td>
        <td>http://netfilter.org/projects/iptables/index.html</td>
    </tr>
    <tr>
        <td>Libsuperuser</td>
        <td>Apache License 2.0</td>
        <td>https://github.com/Chainfire/libsuperuser</td>
    </tr>
    <tr>
        <td>Locale Plugin</td>
        <td>Apache License 2.0</td>
        <td>http://www.twofortyfouram.com</td>
    </tr>
        <td>Material-Dialogs</td>
        <td>MIT License</td>
        <td>https://github.com/afollestad/material-dialogs</td>
    </td>
    <tr>
        <td>Networklog</td>
        <td>Mozilla Public License Version 2.0</td>
        <td>https://github.com/pragma-/networklog</td>
    </tr>
	<tr>
        <td>Root Tools</td>
        <td>Apache License 2.0</td>
        <td>https://github.com/Stericson/RootTools</td>
    </tr>	
    <tr>
        <td>SimpleNoSQL</td>
        <td>Apache License 2.0</td>
        <td>https://github.com/Jearil/NoSimpleNoSQL</td>
    </tr>
</table>

Compile AFWall+
---------------

Prerequisites:

* Install Android Studio 1.5 (or higher)
* Install JavaSDK 1.8 (or higher)
* Install Gradle 1.5 (or higher) and set GRADLE_HOME into your global $PATH


Quick start:

* Open Android Studio and select import from 'Version Control' and then import the afwall+ project from Github
* Compile AFwall+ via Android Studio 
* (optional) Or directly via Gradle command line './gradlew clean assembleDebug' 
* Debug the app and fix/commit your changes
   

For complete step-by-step instructions, please take a look at the [Wiki | HOWTO-Compile-AFWall](https://github.com/ukanth/afwall/wiki/HOWTO-Compile-AFWall).

## Compiling native binaries

On host side you'll need to install:

* [NDK r10](http://developer.android.com/tools/sdk/ndk/index.html), nominally under /opt/android-ndk-r10
* Host-side gcc 4.7, make, etc. (Red Hat "Development Tools" group or Debian build-essential)
* autoconf, automake, and libtool

This command will build the Android binaries and copy them into res/raw/:

    make -C external NDK=/opt/android-ndk-r10

Contributing
------------

Please fork this repository and contribute back using [pull requests](https://github.com/ukanth/afwall/pulls).

Any contributions, large or small, major features, bug fixes, additional language translations, unit/integration tests are welcomed and appreciated but will be thoroughly reviewed and discussed.

Translating
-----------
The res/values-* dirs are kept up to date automatically via Crowdin Translate Extension. See [our translation page](http://crowdin.net/project/afwall) if you would like to contribute.

The application is available in many languages, but if yours is not included, or if it needs updating or improving, please create an account and use the translation system (powered by Crowdin Translate Extension) and make your changes.
