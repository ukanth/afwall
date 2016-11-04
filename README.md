## AFWall+ (Android Firewall+) [![Build Status](https://travis-ci.org/ukanth/afwall.png?branch=beta)](https://travis-ci.org/ukanth/afwall) [![Crowdin](https://d322cqt584bo4o.cloudfront.net/afwall/localized.png)](https://crowdin.net/project/afwall)
[![Donate](https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=6E4VZTULRB8GU)
======

![AFwall+](https://raw.githubusercontent.com/ukanth/afwall/0502e6f17ceda08069720ff2f260902690e65e9b/screenshots/Main_2.0.png)

[![Google Play](https://play.google.com/intl/en_us/badges/images/badge_new.png)](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall)

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
* [Frequently Asked Questions](#frequently-asked-questions)
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
* Support 4.x, 5.x, and 6.x versions of Android
* Easy to install
* Simple to use
* Free and open source
* No advertisements
* Choose your preferred language
* Search for installed applications
* Sort installed applications by installed date/UID/alphabatical order
* Get a notification for any newly installed application with internet permission
* Blocked packets notification and filter
* Password protection
* Device Admin Support (to protect AFWall+ from uninstall)
* Easy to manage your rules with a custom script
* IPv4/IPv6 support
* WiFi, wireless data, LAN, VPN, tether, and roaming support
* Tasker and Locale support
* Firewall Logs service
* Multi-User (multiple-profiles) support
* Built-in iptables/BusyBox
* Export and import rules (Import All Rules needs donate version)
* Option to prevent data leaks during boot (requires *init.d* support or *S-OFF*)
* ARM/MIPS/x86 processor support

Bug Reports
--------

Please see the [issues](https://github.com/ukanth/afwall/issues) section to
report any bugs or feature requests and to see the list of known issues. Before you report a bug, please take a look [here](https://github.com/ukanth/afwall/wiki/HOWTO-Report-Bug).  

Limitations
-----------

* A firewall cannot protect against attacks that are performed outside the operating point. For example, if there is a Point-to-Point connection to the internet.
* A firewall cannot prevent corporate data from being copied to a memory stick or HDD. 
* AFWall+ does not scan for virus/malware that may exist in the data that passes through it, because a firewall is not an antivirus solution.

Compatibility
-------------

AFWall+ has been successfully tested with Android version 4.x, 5.x, and 6.x (ICS, JellyBean, KitKat, Lollipop, Marshmallow) and is reported to work with most Android variants, including stock ROMs.

It is not recommended to use AFWall+ in combination with any similar solution because this could result in conflicts and potential data leaks (iptables could be overwritten).

Upgrading
---------

* **Make a backup** (e.g. with Titanium Backup)
* **Do not remove the previous version** (otherwise your settings might get lost)
* Download the new version
* Install the new version over the previous version
* Done!

Permissions
-----------

AFWall+ asks for the following Android permissions:

* RECEIVE_BOOT_COMPLETED: Allows AFWall+ to autostart after Android finishes booting.
* ACCESS_NETWORK_STATE: Allows AFWall+ to access information about networks (iptables).
* WRITE_EXTERNAL_STORAGE: Allows AFWall+ to write to external storage for the debug log and to export iptables rules.
* ACCESS_SUPERUSER: Standard to support Superuser/SuperSU (by Koushik/Chainfire).
* INTERNET: NetworkInterface.getNetworkInterfaces() needs android.permission.INTERNET. This is just being used to get the IPv4 and IPv6 addresses/subnets for each interface, so the LAN address ranges can be determined. Nothing is actually trying to access the network. Also take a look at [Stackoverflow](http://stackoverflow.com/questions/17252018/getting-my-lan-ip-address-192-168-xxxx-ipv4).
* ACCESS_WIFI_STATE: Allows AFWALL+ to detect the tether state.

Frequently Asked Questions
--------------------------

Having a problem with AFWall+? Check out the [FAQ](https://github.com/ukanth/afwall/wiki/FAQ) before reporting a bug or problem that may already be known.

License
-------

AFWall+ is under the [GNU General Public License v3.0 License](https://www.gnu.org/licenses/gpl.html).

Acknowledgements
----------------

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
        <td>https://www.busybox.net</td>
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
        <td>https://netfilter.org/projects/iptables/index.html</td>
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

* Android SDK in your `$PATH` (both `platform-tools/` and `tools/` directories)
* Javac 1.7 (or higher) and a recent version of Apache *ant* in your `$PATH`
* Git in your `$PATH`
* Use the Android SDK Manager to install API 19 (or higher)

Quick start:

    git clone git://github.com/ukanth/afwall
    cd afwall
    ./gradlew clean assembleDebug

For complete instructions, please take a look at the [How To Compile AFWall+ Wiki](https://github.com/ukanth/afwall/wiki/HOWTO-Compile-AFWall).

## Compiling Native Binaries

On the host side, you will need to install:

* [NDK r10](https://developer.android.com/ndk/index.html), nominally under `/opt/android-ndk-r10`
* Host-side gcc 4.7, make, etc. (Red Hat "Development Tools" group or Debian build-essential)
* autoconf, automake, and libtool

This command will build the Android binaries and copy them into `res/raw/`:

    make -C external NDK=/opt/android-ndk-r10

Contributing
------------

Please fork this repository and contribute back using [pull requests](https://github.com/ukanth/afwall/pulls).

All contributions, large or small, major features, bug fixes, additional language translations, unit/integration tests are welcomed and appreciated and will be thoroughly reviewed and discussed.

Translating
-----------
The `res/values-*` dirs are kept up to date automatically via the Crowdin Translate Extension. See [our translation page](http://crowdin.net/project/afwall) if you would like to contribute.

This application is available in many languages, but if yours is not included, or if it needs updating or improving, please create an account and use the translation system (powered by Crowdin Translate Extension) and make your changes.
