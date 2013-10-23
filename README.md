AFWall+ ( Android Firewall +) [![Build Status](https://travis-ci.org/github/android.png)](https://travis-ci.org/github/afwall)
======

![AFwall+](http://s1.directupload.net/images/121120/zg3xi7w9.png)

Android Firewall+ is a advance iptables editor (GUI) for Android. It's an improved version of [DroidWall](http://code.google.com/p/droidwall), the original owner and creator Rodrigo Rosauro sold Droidwall to AVAST in december 2011.

<br>For advance Information and community talk please look [at XDA](http://forum.xda-developers.com/showthread.php?t=1957231) or [our WIKI](https://github.com/ukanth/afwall/wiki).

[![Google Play](http://developer.android.com/images/brand/en_generic_rgb_wo_45.png)](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall)

[Changelog](https://github.com/ukanth/afwall/blob/master/Changelog.md)
======

## Bug Report
Please see the [issues](https://github.com/ukanth/afwall/issues) section to
report any bugs or feature requests and to see the list of known issues. Before you report an bug, take a look [here](https://github.com/ukanth/afwall/wiki/HOWTO-Report-Bug).  


## FAQ
You have problems with AFWall+? Check out the [FAQ](https://github.com/ukanth/afwall/wiki/FAQ) before reporting a bug or problem that maybe already known.


## ToDo:
* All Bug fixes from original [DroidWall](http://code.google.com/p/droidwall/)
* Integrate with Network Log 
* Uninstall application from list window ( or open manage application from list)
* All Bugs [you reporting](https://github.com/ukanth/afwall/issues)


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
Quick start:

    git clone git://github.com/ukanth/afwall
    cd afwall
    git submodule init
    git submodule update
    android update project -p . -s
    ant debug

For complete instructions, please take a look at the [Wiki | HOWTO-Compile-AFWall](https://github.com/ukanth/afwall/wiki/HOWTO-Compile-AFWall).

## Contributing

Please fork this repository and contribute back using
[pull requests](https://github.com/ukanth/afwall/pulls).

Any contributions, large or small, major features, bug fixes, additional
language translations, unit/integration tests are welcomed and appreciated
but will be thoroughly reviewed and discussed.
