## AFWall+ (Android Firewall+)
![Android CI](https://github.com/ukanth/afwall/workflows/Android%20CI/badge.svg?branch=beta) [![Crowdin](https://d322cqt584bo4o.cloudfront.net/afwall/localized.png)](https://crowdin.net/project/afwall) ![GitHub](https://img.shields.io/github/license/ukanth/afwall)  ![F-Droid](https://img.shields.io/f-droid/v/dev.ukanth.ufirewall) ![GitHub All Releases](https://img.shields.io/github/downloads/ukanth/afwall/total) ![GitHub repo size](https://img.shields.io/github/repo-size/ukanth/afwall)

Description
-----------

Android Firewall+ (AFWall+) is an advanced iptables editor (GUI) for Android. It provides fine-grained control over which Android apps are allowed to access the network.


For more information and a community discussion ground, please visit the official [XDA thread](http://forum.xda-developers.com/showthread.php?t=1957231) or the official [Wiki page](https://github.com/ukanth/afwall/wiki).

<img src="https://raw.githubusercontent.com/ukanth/afwall/0502e6f17ceda08069720ff2f260902690e65e9b/screenshots/Main_2.0.png" width="300">


[![Google Play](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall) 

Index
-----

* [Description](#description)
* [Availability](#availability)
* [Supports](#supports)
* [Highlights](#highlights)
* [Features](#features)
* [Bug Reports](#bug-reports)
* [Limitations](#limitations)
* [Compatibility](#compatibility)
* [Upgrading](#upgrading)
* [Permissions](#permissions)
* [Frequently Asked Questions (FAQ)](#frequently-asked-questions)
* [License](#license)
* [Acknowledgements](#acknowledgements)
* [Compiling AFWall+](#compiling-the-apk)
* [Compile Native Binaries](#compiling-native-binaries)
* [Contributing](#contributing)
* [Translating](#translating)
* [Donations](#donations)


Availability
------------
AFWall can be downloaded via [Google Play Store](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall), [GitHub](https://github.com/ukanth/afwall/releases) or via [F-Droid](https://f-droid.org/repository/browse/?fdid=dev.ukanth.ufirewall).

The [changelog](https://github.com/ukanth/afwall/blob/beta/Changelog.md) documents changes between each new release.

Supports
--------
* Android versions 5.x to 9.x
    for 4.x - 2.9.9 
    for 2.x - 1.3.4.1
* Compatible with Magisk and LineageOS su.    
* ARM/MIPS/x86 processors
* IPv4 & IPv6 protocols
* WiFi, mobile data, LAN, VPN, tether, roaming and Tor
* Multi-user (multiple profiles)
* Many languages *(see [Translating](#translating))*
* Tasker and Locale plugin
* Xposed plugin

Highlights
----------
* Easy to install
* Simple to use
* Free & open source
* No advertisements
* Built-in IPtables/BusyBox

Features
--------
* List and search for all installed applications
* Sort installed applications by installation date, [UUID](https://developer.android.com/reference/java/util/UUID) or in alphabatical order
* Receive notification for any newly installed application, AFwall only list app with INTERNET_PERMISSION
* AFWall comes with it's logs service to see what's going on
* Display notifcations for blocked packets
* Filter blocked packet notifications per app
* Export & import rules ("Import All Rules" requires the donate version)
* Option to prevent data leaks during boot (requires *init.d* support or *S-OFF*)
* Password protection
* Option to manage iptable rules with a custom script
* ~~Device Admin to protect AFWall+ from uninstall~~ (see [here](https://developers.google.com/android/work/device-admin-deprecation) why it was removed)

Bug Reports
-----------

Please check GitHub's [issues](https://github.com/ukanth/afwall/issues) section for existing bugs and in case you like to submit a new one. Feature requests are also welcome. 

Before you report any problem/bug, take a look into the [how-to-report a bug](https://github.com/ukanth/afwall/wiki/HOWTO-Report-Bug) section.

Limitations
-----------

* A firewall cannot protect against attacks that are performed outside the operating point. For example, if there is a Point-to-Point connection to the Internet.
* A firewall cannot prevent corporate data from being copied to a memory stick or HDD, and having these removed from the building. 
* AFWall+ does not scan for virus/malware that may exist in the files that pass through it, because it is a firewall and not an antivirus solution.
* AFWall+ is not an ad-blocker.
* Some apps/script which are running under admin rights might bypassing AFWall because they overwrite the system own IPtables with their own rules. Make sure you only give trusted application superuser rights, most "su"-solutions have companion apps which showing which apps are running under which rights like MagisK, Chainfire's su etc.

Compatibility
-------------

AFWall+ has been successfully tested under Android versions 4.x - 9.x. and is reported to work with most Android variants, including stock or exotic ROMs.

We do not recommend using AFWall+ in combination with any of the similar solutions (Avast, Kaspersky, NetGuard etc) because this could result in conflicts or even data leaks (e.g. IPtables could get overwritten).

Upgrading
---------

The upgrading mechanism is really simple, basically you can just "over-install" the new version over the old one, however this is the best pratice (which we recommended):

* **Make a backup of the current version** (e.g. via Titanium Backup).
* **Do not remove the current version** (otherwise your settings might getting reset).
* Download the latest AFWall+ version.
* Install the new version over the previous version.
* Done!

Permissions
-----------

AFWall+ asks for the following [Android permissions](https://developer.android.com/guide/topics/permissions/overview):

* RECEIVE_BOOT_COMPLETED: Autostart (Bootup) AFWall+ after the system finishes booting.
* ACCESS_NETWORK_STATE: Allows AFWall+ to access information about networks (iptables).
* WRITE_EXTERNAL_STORAGE: Allows AFWall+ to write to external storage for debug log and export iptables rules.
* INTERNET: NetworkInterface.getNetworkInterfaces() needs android.permission.INTERNET. This is just being used to get the IPv4 and IPv6 addresses/subnets for each interface, so the LAN address ranges can be determined. Nothing is actually trying to access the network. Also take a look at [Stackoverflow](https://stackoverflow.com/questions/17252018/getting-my-lan-ip-address-192-168-xxxx-ipv4).
* ACCESS_WIFI_STATE: Used to detect the tether state.
* DEPRECATED ~~ACCESS_SUPERUSER: Standard to support Superuser/SuperSU (by Koushik/Chainfire)~~

Frequently Asked Questions
-----------

Having some problems with AFWall+? Check out our [FAQ](https://github.com/ukanth/afwall/wiki/FAQ) before reporting a bug or problem that may already be known or answered.

License
-------

AFWall+ is released under the [GNU General Public License v3.0 License](https://www.gnu.org/licenses/gpl.html).

Acknowledgements
----------------

The original codebase was derived from [DroidWall](http://code.google.com/p/droidwall) by Rodrigo Rosauro. DroidWall was sold to AVAST in December 2011, and is no longer actively maintained.

This project also uses some other open-source libraries such as:

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
        <td>DBFlow</td>
        <td>MIT</td>
        <td>https://github.com/Raizlabs/DBFlow</td>
    </tr>
	<tr>
        <td>Prettytime</td>
        <td>Apache License 2.0</td>
        <td>https://github.com/ocpsoft/prettytime</td>
    </tr>
    <tr>
        <td>material-dialogs</td>
        <td>MIT License</td>
        <td>https://github.com/afollestad/material-dialogs</td>
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
</table>

Compiling the APK
---------------

Prerequisites:

* Android SDK in your $PATH (both platform-tools/ and tools/ directories)
* Javac 1.7 (or higher) and a recent version of Apache ant in your $PATH
* Git should be added in your $PATH
* Use the Android SDK Manager to install API 19 (or higher)

Quick start:

    git clone git://github.com/ukanth/afwall
    cd afwall
    ./gradlew clean assembleDebug

For complete instructions, please take a look at the [Wiki's How To Compile AFWAll section](https://github.com/ukanth/afwall/wiki/HOWTO-Compile-AFWall).

## Compiling Native Binaries

You can compile the external binaries like BusyBox or the IPtables yourself, on the host side, you'll need to install the following:

* [NDK r10](http://developer.android.com/tools/sdk/ndk/index.html), nominally under /opt/android-ndk-r10
* Host-side gcc 4.7, make, etc. (Red Hat 'Development Tools' group or Debian build-essential)
* autoconf, automake, and libtool

This command will build the Android binaries and copy them into `res/raw/`:

    make -C external NDK=/opt/android-ndk-r10

Contributing
------------

You can fork the repository and contribute using [pull requests](https://github.com/ukanth/afwall/pulls).

All contributions no matter if large or small, major features, bug fixes, additional language translations, unit/integration tests are welcomed and appreciated. The pull requests and findings are usually getting reviewed and discussed with the developer and the community .

Translating
-----------
The `res/values-*` dirs are kept up-to-date automatically via the [Crowdin Translate Extension](https://github.com/marketplace/crowdin). See [our official translation page](http://crowdin.net/project/afwall) in case you like to contribute.

AFWall+ is available in many languages but if yours is not included - or if it needs updating or improving - please create an account and use the translation system and commit your changes.


Donations
-----------

Donations are **optional** and helps the project in order to keep up the development. The official donation link is the one below which points to the official AFWall+ PayPal account. You optionally can buy the unlocker key via Google Play Store which unlocks additional features in AFWall+, the unlocker is not avbl. via F-Droid.

[![Donate](https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=6E4VZTULRB8GU)
