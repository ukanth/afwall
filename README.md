AFWall+ ( Android Firewall +) [![Build Status](https://travis-ci.org/ukanth/afwall.png?branch=beta)](https://travis-ci.org/ukanth/afwall)[![Crowdin](https://d322cqt584bo4o.cloudfront.net/afwall/localized.png)](https://crowdin.net/project/afwall)
======

![AFwall+](http://s1.directupload.net/images/121120/zg3xi7w9.png)


Android Firewall+ is an advanced iptables editor (GUI) for Android. It provides fine-grained control over which Android apps are allowed to
access the network.

The original codebase was derived from [DroidWall](http://code.google.com/p/droidwall) by Rodrigo Rosauro. DroidWall was sold to AVAST
in December 2011, and is no longer actively maintained.

For more information and community discussions, please visit the [XDA thread](http://forum.xda-developers.com/showthread.php?t=1957231) or [our WIKI](https://github.com/ukanth/afwall/wiki).

[![Google Play](http://developer.android.com/images/brand/en_generic_rgb_wo_45.png)](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall)

[![Support via Gittip](https://rawgithub.com/twolfson/gittip-badge/0.2.0/dist/gittip.png)](https://www.gittip.com/ukanth/)

## Changelog

The AFWall+ version history is maintained [here](https://github.com/ukanth/afwall/blob/beta/Changelog.md).

## Bug Reports
Please see the [issues](https://github.com/ukanth/afwall/issues) section to
report any bugs or feature requests and to see the list of known issues. Before you report a bug, take a look [here](https://github.com/ukanth/afwall/wiki/HOWTO-Report-Bug).  


## FAQ
Having problems with AFWall+? Check out the [FAQ](https://github.com/ukanth/afwall/wiki/FAQ) before reporting a bug or problem that may already be known.


## ToDo:
* All Bug fixes from original [DroidWall](http://code.google.com/p/droidwall/)
* Integrate with Network Log 
* Uninstall application from list window (or open/manage application from list)
* All bugs [reported by users](https://github.com/ukanth/afwall/issues)


## Already done
* ~~New GUI~~ <i>(since 1.0.1a)</i>
* ~~Roaming option ( like avast firewall )~~ <i>(since 1.0.2a)</i>
* ~~Flush/Reset iptables rules from UI~~ <i>(since 1.0.2a)</i>
* ~~Save & Load Profiles/Rules~~ <i>(since 1.0.3a)</i>
* ~~Full i18n support~~ <i>(since 1.0.3a)</i>
* ~~View iptables rules and logging in a clear view~~ <i>(since 1.0.4a)</i>
* ~~Support for [Tasker](http://tasker.dinglisch.net/) & [Locale](http://www.twofortyfouram.com/)~~ <i>(since 1.0.4a)</i>
* ~~Multiple Profiles~~ <i> (Since 1.0.7a)</i>
* ~~Support for only within LAN/WAN~~ <i> (Since 1.2.5)</i> 



## License

* AFwall+ is under the [GNU General Public License v3.0 License](https://www.gnu.org/licenses/gpl.html)
 

## Acknowledgements

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



## How to compile AFWall+

Prerequisites:

* Android SDK in your $PATH (both platform-tools/ and tools/ directories)
* javac 1.6 and a recent version of Apache ant in your $PATH
* git in your $PATH
* Use the Android SDK Manager to install APIs 14, 16, and 19

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

## Contributing

Please fork this repository and contribute back using
[pull requests](https://github.com/ukanth/afwall/pulls).

Any contributions, large or small, major features, bug fixes, additional
language translations, unit/integration tests are welcomed and appreciated
but will be thoroughly reviewed and discussed.
