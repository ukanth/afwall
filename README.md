## AFWall+ (Android Firewall+) [![Build Status](https://travis-ci.org/ukanth/afwall.png?branch=beta)](https://travis-ci.org/ukanth/afwall) [![Crowdin](https://d322cqt584bo4o.cloudfront.net/afwall/localized.png)](https://crowdin.net/project/afwall)
[![Donate](https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=6E4VZTULRB8GU)
======

![AFwall+](https://raw.githubusercontent.com/ukanth/afwall/0502e6f17ceda08069720ff2f260902690e65e9b/screenshots/Main_2.0.png)

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
* [Frequently Asked Questions (FAQ)](#frequently-asked-questions-FAQ)
* [License](#license)
* [Acknowledgements](#acknowledgements)
* [Compile AFWall+](#compile-afwall)
* [Compile Native Binaries](#compiling-native-binaries)
* [Contributing](#contributing)
* [Translating](#translating)

Description
-----------

Android Firewall+ (AFWall+) is an advanced iptables editor (GUI) for Android. It provides fine-grained control over which Android apps are allowed to access the network.

For more information and community discussions, please visit the [XDA thread](http://forum.xda-developers.com/showthread.php?t=1957231) or [our Wiki](https://github.com/ukanth/afwall/wiki).

Availability
------------
Download the latest release from the [Google Play Store](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall), [GitHub](https://github.com/ukanth/afwall/releases), or [F-Droid](https://f-droid.org/repository/browse/?fdid=dev.ukanth.ufirewall)

The [changelog](https://github.com/ukanth/afwall/blob/beta/Changelog.md) documents changes between each release.

Supports
--------
* Android versions 4.x/5.x/6.x/7.x
* ARM/MIPS/x86 processors
* IPv4/IPv6 protocols
* WiFi, mobile data, LAN, VPN, tether, and roaming
* Multi-user (multiple profiles)
* Many languages *(see [Translating](#translating))*
* Tasker and Locale apps

Highlights
----------
* Easy to install
* Simple to use
* Free and open source
* No advertisements
* Built-in iptables/BusyBox

Features
--------
* Search for installed applications
* Sort installed applications by installed date/UID/alphabatical order
* Receive notification for any newly installed application with internet permission
* Firewall logs service
* Optionally display notifcations for blocked packets
* Filter blocked packet notifications per app
* Export and import rules (Import All Rules requires donate version)
* Option to prevent data leaks during boot (requires *init.d* support or *S-OFF*)
* Optional Password protection
* Option to manage rules with a custom script
* Option to enable Device Admin to protect AFWall+ from uninstall

Bug Reports
-----------

Please see the [issues](https://github.com/ukanth/afwall/issues) section to
report any bugs, make feature requests, and to see the list of known issues. Before you report a bug, take a look [here](https://github.com/ukanth/afwall/wiki/HOWTO-Report-Bug).

Limitations
-----------

* A firewall cannot protect against attacks that are performed outside the operating point. For example, if there is a Point-to-Point connection to the Internet.
* A firewall cannot prevent corporate data from being copied to a memory stick or HDD, and having these removed from the building. 
* AFWall+ does not scan for virus/malware that may exist in the files that pass through it, because it is a firewall and not an antivirus solution.

Compatibility
-------------

AFWall+ has been successfully tested with Android versions 4.x - 7.x. (ICS, JellyBean, KitKat, Lollipop, Marshmallow, Nougat) and is reported to work with most Android variants, including stock ROMs.

We do not recommend using AFWall+ in combination with any of the similar solutions because this could result in conflicts and potential data leaks (iptables could get overwritten).

Upgrading
---------

* **Make a backup of the current version** (e.g. using Titanium Backup).
* **Do not remove the current version** (otherwise your settings might get reset).
* Download the new version.
* Install the new version over the previous version.
* Done!

Permissions
-----------

AFWall+ asks for the following Android permissions:

* RECEIVE_BOOT_COMPLETED: Autostart (Bootup) AFWall+ after the system finishes booting.
* ACCESS_NETWORK_STATE: Allows AFWall+ to access information about networks (iptables).
* WRITE_EXTERNAL_STORAGE: Allows AFWall+ to write to external storage for debug log and export iptables rules.
* ACCESS_SUPERUSER: Standard to support Superuser/SuperSU (by Koushik/Chainfire).
* INTERNET: NetworkInterface.getNetworkInterfaces() needs android.permission.INTERNET. This is just being used to get the IPv4 and IPv6 addresses/subnets for each interface, so the LAN address ranges can be determined. Nothing is actually trying to access the network. Also take a look at [Stackoverflow](https://stackoverflow.com/questions/17252018/getting-my-lan-ip-address-192-168-xxxx-ipv4).
* ACCESS_WIFI_STATE: Used to detect the tether state.

Frequently Asked Questions (FAQ)
--------------------------------

Having problems with AFWall+? Check out our [FAQ](https://github.com/ukanth/afwall/wiki/FAQ) before reporting a bug or problem that may already be known.

License
-------

AFWall+ is released under the [GNU General Public License v3.0 License](https://www.gnu.org/licenses/gpl.html).

Acknowledgements
----------------

The original codebase was derived from [DroidWall](http://code.google.com/p/droidwall) by Rodrigo Rosauro. DroidWall was sold to AVAST in December 2011, and is no longer actively maintained.

This project also uses many other open-source libraries such as:

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

Compile AFWall+
---------------

Prerequisites:

* Android SDK in your $PATH (both platform-tools/ and tools/ directories)
* Javac 1.7 (or higher) and a recent version of Apache ant in your $PATH
* Git in your $PATH
* Use the Android SDK Manager to install API 19 (or higher)

Quick start:

    git clone git://github.com/ukanth/afwall
    cd afwall
    ./gradlew clean assembleDebug

For complete instructions, please take a look at the [Wiki's How To Compile AFWAll section](https://github.com/ukanth/afwall/wiki/HOWTO-Compile-AFWall).

## Compiling Native Binaries

On the host side, you will need to install:

* [NDK r10](http://developer.android.com/tools/sdk/ndk/index.html), nominally under /opt/android-ndk-r10
* Host-side gcc 4.7, make, etc. (Red Hat 'Development Tools' group or Debian build-essential)
* autoconf, automake, and libtool

This command will build the Android binaries and copy them into `res/raw/`:

    make -C external NDK=/opt/android-ndk-r10

Contributing
------------

Please fork this repository and contribute back using [pull requests](https://github.com/ukanth/afwall/pulls).

All contributions, large or small, major features, bug fixes, additional language translations, unit/integration tests are welcomed and appreciated but will be thoroughly reviewed and discussed.

Translating
-----------
The `res/values-*` dirs are kept up to date automatically via the Crowdin Translate Extension. See [our translation page](http://crowdin.net/project/afwall) if you would like to contribute.

This application is available in many languages, but if yours is not included, or if it needs updating or improving, please create an account and use the translation system (powered by the Crowdin Translate Extension) and make your changes.
