Changelog AFWall+
=============

-------------
Download links are right here on the ["Downloads" front page](https://github.com/ukanth/afwall/downloads).

Version 1.1.3
* Critical bugfix : Rules were not applied after every system reboot !

Version 1.1.2
* Minor bug fix for Forceclose on alerts !


Version 1.1.1
* Feature : Tasker/Locate Plugin! (Only for donate version for now)
* Feature : Now allow customize names for profiles.(from preferences)
* Feature : Replace alert/toasts with appmsg(displays within the app) - enable it in preference
* Feature : Initial simple improvements for view logs. It will be improved further!
* Preference : Added new preference to enable confirmbox on firewall disable
* FC : Replaced old style deprecated Thread implementation with AsyncTask.(faster and safer)
* FC : NullPointer exception while reading preferences.
* Bug Fix : Shutdown Custom Rule doesn't work.
* Bug Fix : Refresh issue of mode on the multiple profiles switching.
* Bug Fix : Fixed two identical profile names on multiple profiles.
* i18n : Completed french/Germen translations.
* i18n : Added russian language support(Thanks : Google translator toolkit )
* and many small fixes

Version 1.1.0
* Initial Version (Playstore)

Version 1.0.7a
* New Icon for AFWall ( Thanks for hush66 !)
* Multiple Profiles ( Currently limited to 4 )
* Added support for Epic 4g Touch( Thanks to JoshMiers !)
* Unified Preferences (https://github.com/saik0/UnifiedPreference/)
* Translations added (french/german)
* Fixed multiple menu on ActionBar with staticbar ( no more two menu items on some devices )
* Enable/Disable logs now moved to preferences and log menu will be hidden if disabled
* Bug Fix : Update of application packages will not be notified with AFwall.
* Bug Fix : Uninstalling app will reset rule for root application to default.

Version: 1.0.6.1a
* Bug Fix : Rules for spcial application were not applied after application restart.

Version: 1.0.6a
* Now uses Chainfire's SU library, This will get rid of old shell script approach. I feel it's faster and better approach and helps to enable profiles !
  Please Note: If you use afwall.sh outside, starting this version it will not work !
* Improved menubar and confirmation dialogs
* Fixed bug with logging 
* Fixed bug on some ICS/JB devices
* Added new EXPERIMENTAL option for ICS/JB devices ( uses extra rules )
* Enabled fast scrolling on lists ( main list )
* German Translation ( Thanks to CHEF !)
* Support for 4.2 JB


Version 1.0.5a

* Enhanced Rules view with additional actions like copy, flush, export to sdcard and network interfaces.
* Moved flush rules from main menu to enhanced rules view
* Enhanced Log view with additional copy & clear action
* Moved clear log from main menu to enhanced log view
* Fixed FC issues from 1.0.4.x

Version 1.0.4.1a
* Fixed force close on viewlog and view rules pages.

Version 1.0.4a
* Import/Export Rules ( for now it's just a single import&export to External storage)
* Integrated search bar ( application search )
* Revamped Log & IPTables rules view ( you can now view the logs and rules in a clear view and copy them !)
* Added reenter password confirmation dialog.
* Added additional ifaces to support more devices ( working on another solution which will identity interfaces on the particular device)
* Fixed force close when scrolling for some devices
* and many. 

Version 1.0.3a
* Fix for some apps can "bypass" the firewall by just using UDP port 53. Disable port 53
* Added 3g ifaces to support more devices ( should solve issues with firewall for some devices )
* Fixed Widget on/off issue ( First enable firewall and then add the widget will do the trick !)
* Fixed Widget size for 4.0+ devices
* Prepared for i18n Support
* Prepared support for XHDPI devices

Note: If you have any issue, please clean the rules via menu and apply again.

Version 1.0.2a ( Please note, if you upgrade from 1.0.1a, rules will be reset!)
* Roaming Option ( not tested !)
* Added Shutdown broadcast and applied rule to block all the connections ( this should solve the leakage 
  when phone is rebooted/started before afwall can start !!!) - Not tested !
* Added option to disable application icon ( faster loading on slow devices )
* Added option to disable 3g rules when connected via Wired USB (Droidwall issue)
* Added support for more ifaces for 3G ( support multiple devices )
* Added clear Rules option in menu (now the iptables will be saved as afwall-3g,afwall-wifi, to solve the issue when both Droidwall & AFWall+ installed )
* Fixed bug in reload applications
* Fixed bug in applying rules in clear/select all
* Fixed the issue with save/discard rules when press back button.

Version 1.0.1a
* Improved install notification ( only notify when app has internet permission )
* Select All Wifi / 3G or Clean All option ! ( HUGE FIX ) - No Invert select this time. just click on the 3g/wifi icons will do the trick !
* Fixed dangerous file permissions issues ( reported in original Droidwall issue )

Version 1.0.0a
* Initial version
* Based on [DroidWall](http://code.google.com/p/droidwall/) 1.5.7
* ICS style menubar and theme
* New install notifications
* New preferences options
* Force reload Applications
* Highlight System applications using custom color from preferences
