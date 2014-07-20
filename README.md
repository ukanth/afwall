## AFWall+ (Android Firewall+) 
[![Build Status](https://travis-ci.org/ukanth/afwall.png?branch=beta)](https://travis-ci.org/ukanth/afwall)[![Crowdin](https://d322cqt584bo4o.cloudfront.net/afwall/localized.png)](https://crowdin.net/project/afwall)
======

![AFwall+](http://s1.directupload.net/images/121120/zg3xi7w9.png)

[![Google Play](http://developer.android.com/images/brand/en_generic_rgb_wo_45.png)](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall) [![Support via Gittip](https://rawgithub.com/twolfson/gittip-badge/0.2.0/dist/gittip.png)](https://www.gittip.com/ukanth/)

Index
-----

* [Description](#description)
* [Features](#features)
* [Changelog](https://github.com/ukanth/afwall/blob/beta/Changelog.md)
* [Bug Reports](#Bug-Reports)
* [Limitations](#limitations)
* [Compatibility](#compatibility)
* [Upgrading](#upgrading)
* [Permissions](#permissions)
* [Frequently asked questions](#frequently-asked-questions)
* [ToDo](#todo)
* [Already done](#already-done)
* [License](#license)
* [Acknowledgements](#acknowledgements)
* [Compile AFWall+](#compile-afwall+)
* [Contributing](#contributing)

Description
-----------

Android Firewall+ is an advanced iptables editor (GUI) for Android. It provides fine-grained control over which Android apps are allowed to access the network.

The original codebase was derived from [DroidWall](http://code.google.com/p/droidwall) by Rodrigo Rosauro. DroidWall was sold to AVAST in December 2011, and is no longer actively maintained.

For more information and community discussions, please visit the [XDA thread](http://forum.xda-developers.com/showthread.php?t=1957231) or [our WIKI](https://github.com/ukanth/afwall/wiki).

Features
--------

* Easy to install & simple to use
* Free and open source
* No advertisements
* Choose your preferred language
* Search for installed applications
* Get a notification about new installed application
* Blocked packets notification and filter
* Device Admin Support (Protect AFWall+ from uninstall)
* Professional skills not required for configuration
* Easy to manage your rules with a custom script
* For any (stock) variant of Android version 2.2 - 4.4.4 (ICS, JellyBean, KitKat)
* Ipv4/Ipv6 support
* LAN-, VPN-, Tether-, Roaming-Control
* Tasker and Locale support
* Firewall Logs service
* Multi-User (multiple-profiles) support
* Build-In Iptables/Busybox
* Export and Import rules (Import DroidWall Rules needs donate version)
* Option to prevent data leaks during boot (REQUIRES init.d support or S-OFF)
* Supports MIPS/x86/ARM

Bug Reports
--------

Please see the [issues](https://github.com/ukanth/afwall/issues) section to
report any bugs or feature requests and to see the list of known issues. Before you report a bug, take a look [here](https://github.com/ukanth/afwall/wiki/HOWTO-Report-Bug).  

Limitations
-----------

* A firewall cannot protect against attacks that are performed outside the operating point. For example, if there is a Point-TO-POINT to the Internet.
* The firewall cannot prevent corporate data from being copied to a memory stick or hdd and these are subtracted from the building. 
* AFWall+ cannot have a precise system of SCAN for each type of virus/malware that may arise in the files that pass through it, because the firewall is not an antivirus.

Compatibility
-------------

AFWall+ has been sucess tested with Android version 2.2 - 4.4.4 (ICS, JellyBean, KitKat) and is reported to work with most Android variants, including stock ROMs.
Android L need a little configuration change and a external Busybox app)

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

* Boot: to be able to check if AFWall+ is enabled
* Storage: to be able to export the settings to the SD card
* Internet: send error logs 
* Network access: on connectivity change, apply the rules again

Frequently asked questions
--------------------------

Having problems with AFWall+? Check out the [FAQ](https://github.com/ukanth/afwall/wiki/FAQ) before reporting a bug or problem that may already be known.

ToDo
-----

* Fix all bugs from original [DroidWall](http://code.google.com/p/droidwall/)
* Fix all bugs [reported by users](https://github.com/ukanth/afwall/issues)
* Uninstall application from list window (or open/manage application from list)

Already done
------------

* ~~New GUI~~ <i>(since 1.0.1a)</i>
* ~~Roaming option ( like avast firewall )~~ <i>(1.0.2a)</i>
* ~~Flush/Reset iptables rules from UI~~ <i>(1.0.2a)</i>
* ~~Save & Load Profiles/Rules~~ <i>(1.0.3a)</i>
* ~~Full i18n support~~ <i>(1.0.3a)</i>
* ~~View iptables rules and logging in a clear view~~ <i>(1.0.4a)</i>
* ~~Support for [Tasker](http://tasker.dinglisch.net/) & [Locale](http://www.twofortyfouram.com/)~~ <i>(1.0.4a)</i>
* ~~Multiple Profiles~~ <i>(1.0.7a)</i>
* ~~Support for IPv6~~ <i>(1.2.4)</i>
* ~~Support for only within LAN/WAN/Tether~~ <i>(1.2.5)</i>
* ~~Support NFLOG~~ <i>(1.2.6)</i> 
* ~~Support for x86/MIPS/ARM devices~~ <i>(1.2.7)</i> 

License
-------

AFWall+ is under the [GNU General Public License v3.0 License](https://www.gnu.org/licenses/gpl.html)

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
        <td>ActionBarSherlock</td>
        <td>Apache License 2.0</td>
        <td>http://actionbarsherlock.com/</td>
    </tr>
    <tr>
        <td>Unified Preferences</td>
        <td>Apache License 2.0</td>
        <td>https://github.com/saik0/UnifiedPreference/</td>
    </tr>
    <tr>
        <td>Android Color Picker</td>
        <td>Apache License 2.0</td>
        <td>https://github.com/attenzione/android-ColorPickerPreference</td>
    </tr>
    <tr>
        <td>Root Tools</td>
        <td>Apache License 2.0</td>
        <td>https://code.google.com/p/roottools/</td>
    </tr>
    <tr>
        <td>Libsuperuser</td>
        <td>Apache License 2.0</td>
        <td>https://github.com/Chainfire/libsuperuser</td>
    </tr>
</table>

Compile AFWall+
---------------

Prerequisites:

* Android SDK in your $PATH (both platform-tools/ and tools/ directories)
* Javac 1.8 (or higher) and a recent version of Apache ant in your $PATH
* Git in your $PATH
* Use the Android SDK Manager to install API 19 (or higher)

Quick start:

    git clone git://github.com/ukanth/afwall
    cd afwall
    git submodule init
    git submodule update
    android update project -p . -s
    ant debug

For complete instructions, please take a look at the [Wiki | HOWTO-Compile-AFWall](https://github.com/ukanth/afwall/wiki/HOWTO-Compile-AFWall).

## Compiling native binaries

On the host side you'll need to install:

* NDK r9, nominally under /opt/android-ndk-r9
* Host-side gcc, make, etc. (Red Hat "Development Tools" group or Debian build-essential)
* autoconf, automake, and libtool

This command will build the Android binaries and copy them into res/raw/:

    make -C external NDK=/opt/android-ndk-r9

Contributing
------------

Please fork this repository and contribute back using [pull requests](https://github.com/ukanth/afwall/pulls).

Any contributions, large or small, major features, bug fixes, additional language translations, unit/integration tests are welcomed and appreciated but will be thoroughly reviewed and discussed.

