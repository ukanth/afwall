Changelog AFWall+
==================

Version 1.3.4
* Feature: Added permanent notification on firewall status (optional)
* Bug: Modified init.d script to support system iptables.
* Bug: Fixed FC on multiple devices when enable/disable
* Bug: Fixed issue with widget when password protected.
* Minor widget enhancement for old android devices
* Updated Translations

Version 1.3.3

[Features]
* Added export & import for preferences/profiles including custom profiles (Donate Version only)
* Custom Script for each Profile
* New combined dialog for import and export
* Encryption for application password - Also resets the old passwords. Please set password again !
[Bugs]
* Fix for LogService FC issues on 4.4
* Fix for new apps not showing on top when profiles are enabled
* Fix for Possible SU leak
[Improvements]
* Improved notification text
* Improved search filter/profile validation logic
* Updated libs


Version 1.3.2
* Added back the old profile switch widget till the new widget gets stable.
* Fix: process leak with log and nflog service. Please do a clean install incase if it doesn't work after update.
* Fix: filter application's not working for block notifications.
* Fix: multiple tasker issues ( profile 2 applies - profile 3 and rules are not applying when using tasker)
* Fix: profile status not getting reflected on main view when changed using tasker/widget
* Fix: new widget not applying rules properly for profiles.
* Fix: Import rules fails when package not found.
* Fix: User reported NPE & Force close issues.

Version 1.3.1.1
* Revert Target SDK to 16 to fix issue with boot rules

Version 1.3.1
* Added experimental filter for block notifications.
* Error report FC
* ip6tables log/toggle issues on most devices
* Widget display issue on some devices #265
* Better root detection and error display when no root
* Fixed FC on experimental Preferences #270
* Widget name issues and better icons
* Performance improvement for multiple profiles
* Reuse of rootshell on new logservice
* Widget profile switch issue
* Apply rules on boot fix for some devices


Version 1.3.0.2
* Bug fixes on 1.3.x 

Version 1.3.0.1
* Old toggle widget is back - Hate you guys :)
* "Allow All Application" option is back - Again hate you guys :)

Version 1.3.0
* New Widget with support for multiple profiles (single widget)
* Updated lockpattern - stealth mode/max retry count
* DNS Proxy to Preferences (By default UDP 53 will be blocked on <4.3)
* More Log information (PORT/PROTOCOL)
* Fixed application list load performance issues
* Fixed bug in preferences
* Support for Wifi-only tab (auto hide data column)
* Block packet notification (exp!) - Log service
* New Icon,User reported bug fixes including tasker plugin
* Translations updated - Indonesian (thx mirulumam)

Version 1.2.9
* Feature: Column level select/invert/unselect
* Feature: New Import/Export (with backward compatiblity)
* Feature: Filter by All/Core/System/User applications
* BugFix: Fixed issue with Multiuser iptables rules
* BugFix: Fixed issue with Tasker plugin (enable/disable/Profile switch)
* UI: Revamped About and added FAQ page.
* User reported bug fixes.

Version 1.2.8
* Features
  * Traffic stats + App detail View (Long press on App Label) Note: A minimal stats and not a complete statistics of traffic details.
  * Add/Remove Additional Profiles
  * Multiuser support for Tablets (Experimental)
  * Custom rules file support (. /path/to/file) 
* Fixed VPN issue with KitKat & Updated libsuperuser library
* Many minor UI enhancements and performance improvements
* Bug Fixes: #154,sdcard mount on startup, user reported crash fixes 

Version 1.2.7
* Improved search functionality & select confirmation.
* added builtin ip6tables support
* Support for x86/MIPS/ARM devices.
* Built-in iptables is upgraded to the latest version.
* Various user reported crash/bug fixes.
* Build scripts updated for f-droid and developer friendly builds (ant)
* Added Hungarian/Turkish Translations and updated other translations

Version 1.2.6.2
* NGLog fixes for various devices including nexus.

Version 1.2.6.1
* LOGS should work now on newer devices which has NFLOG chain.
* New RootShell Service to keep AFWall+ active.
* RootShell - Retry on exit 4 while switching from 3G to WiFi or viceversa.
* Removed alternate startup service from experimental- It works without it.
* Improved Caching Logic, Uninstall apps will now remove cache along with rules.
* Reload applications will remove unused cache.
* New Help page - Added another developer @cernekee)
* Fixed too many prompts when password is enabled.
* Rewritten logic to detect LOG chain.
* Added "error send report" option to help beta testers with more diagnostics information. 
* Special UID for DNS Proxy and NTP ( 4.3 users )
* Added support for custom rules from 1.2.5.2 ( it was broken because of afwall iptables chain change)
* Better tether status check
* Keep alive RootShell on some devices. 


Version 1.2.6
* Lots of Refactor to bring stability and performance. Fixed many issues along with it
  (HUGE THANKS TO cernekee !) 
  New option -> Now enable/disable log from preferences
* New option -> Apply rules on Switch Profiles
* New option -> Active Rules is now optional ( But requires when using LAN/Roaming )
* New option -> Enable inbound connections ( Required for sshd, AirDroid etc)
* New busybox binary for ifconfig
* Rules Log has more information 
* NFLOG support for newer devices

Version 1.2.5.2
* Fixed issue with Wifi blocked on whitelist for couple of devices

Version 1.2.5.1
* Improved search functionality.
* Frequent connectivity change rules will now work only on roaming/lan. this should reduce the number of 
  superuser prompts and reduce lag on some devices.
* Fixed issue with application list not showing.
* Fixed issue with logs where it used to work before.
* Fixed issue with default language (default is set to English, please change it in preference)
* Added translations.


Version 1.2.5 
* Added Tether support. (Thanks to cernekee)
* Added LAN/WAN support. (Thanks to cernekee)
* Added Import from DroidWall (from Donate Version !)
* Fixed issue with special applications not showing in different color(system apps) (Thanks to cernekee)
* Fixed issue with preferences for defauly system application picker (Thanks to cernekee)
* Fixed issue with Language preferences default(Thanks to cernekee)
* Lots of code refactor/bug fixes (Thanks to cernekee!)
* Fixed issue with multiline in search text.
* Minor UI changes on the application list.
* Added selectable iptables/busybox binary
* Added new translations (chinese/greek etc.,)


Version 1.2.4.1
* Fixed issue with cleanup afwall rules on disable
* Fixed issue with OUTPUT chain not removed for afwall on disable

Version 1.2.4(bump version to match Donate version)
* Support IPv6 (Enable it in preference)
* Tasker support enable/disable of AFWall+
* Improved performance of applying rules and application list.
* Improved application loading progress dialog.
* Show keyboard automatically on password protected dialogs
* Fixed issue with custom script hangs.
* Improved translations strings.
* Fixed issue with multiple password request (in beta testing)
* Improved detection logic for data leak prevention script (Thanks GermainZ)
* Improved multiple profile performance while loading applications. It will no longer apply rules on switching
 profiles. You need to manually apply rules after profile switch.
* Added translations for Greek,Produguese languages. 


Version 1.2.1
* Minor issue fixed for "Media Server" not apply properly after reboot
* Fixed iptables rules which breaks wifi/Mobile data limit.
* Updated translations for German/Chineese
* Added Swedish Translation - Many Thanks to CreepyLinguist@Crowdin

Version 1.2.0 

* [Feature]
* Added change app language from the preferences (default is system lang)
* Added device admin feature - Extra protection to AFWall+, so that it can't be uninstalled from any other app.
* Added Tasker/Locale plugin (from donate version) with bug fixes.
* Added VPN Support (enable/disable it preferences) - Tested with DroidVPN and works fine !
* Added new widget with quick toggle (enable/disable/profiles)
* Added option to import from DroidWall (only for Donate version for now !)
* Added Active defense ( Make sure only AFWall+ able to control to internet) - Not an optional !
* Added new super user permission ( koush's superuser permission)
* Added ability to enable/disable roaming feature

* [Enhancements]
* New logic to apply rules - Performance improvement 
* Removed deprecated API's for Notification. Going forward this will be improved for ICS/JB 
* Improved preferences - Added summary for each preferences and rearranged order
* New menu icons ( white icons !)
* Removed all inline style alert messages and alert boxes. Now it just display toast messages.
* Fixed data leak on boot for devices REQUIRES init.d support/S-OFF (enable it in preferences - EXPERIMENTAL !)
( to enable init.d support use this app -> https://play.google.com/store/apps/d...k.initdtoggler)
* New log rule to get the logs from dmesg and enable logs by default 
* Enable/Disable logs now from "Firewall Logs" menu.

* [BUG Fix]
* Fixed issue with iptable rules are not applying after reboot, mainly CM 10.1 devices (Enable it in preferences - EXPERIMENTAL !)
* Various UI glitches in multi profiles/icons & UID
* Fixed hang/rules issue on startup 
* Fixed issue with profiles where the default profile is applied after restart instead of selected one.
* FC issue when using app menu (ActionBarSherlock - NPE)
* Fixed issue with Media Server/VPN not applying properly.	


* [Translations]
* Simplified Chinese - Thanks to wufei0513 & tianchaoren@Crowdin
* Czech Translations - Thanks to syk3s@Crowdin
* Turkish Translations - Many Thanks to metah@Crowdin
* Ukrainian Translations - Many Thanks to andriykopanytsia,igor@Crowdin	

Version 1.1.9
* Added invert selection for apps ( useful when switching whitelist <-> blacklist )
* Fixed issue with special apps (root/shell/media server) not applying 
* Fixed issue with new lockpattern not working properly.
* Added MDPI images for icons.
* Code cleanup, mainly strings.xml ( removed version from strings.xml etc.,)

Version 1.1.8
* Fixed FC on new lockpattern

Version 1.1.7
* Added lockpattern ( you can still use the old style password protection ) with SHA1 protection
* Fixed force close issue while adding system apps.
* Fixed issue with select All/none. it wroks properly and doesn't require scroll. Thanks to Pragma !
* Significant improvements while loading applications( hope not a placebo ?)  
* Fixed issue with search case sensitive and expand search will show the keyboard ( no more two press !)
* Disable notification when the firewall is disabled.
* Added new language translations
  - Spanish translations by spezzino@crowdin
  - Dutch translations by DutchWaG@crowdin
  - Japanese translation by nnnn@crowdin
  - Ukrainian translation by andriykopanytsia@crowdin

Version 1.1.6
* Back to Chainfire's SU library. More stable but little slower compare to RootTools. Performance will be improved going forward.
  I'm planning to rewrite the entire code to make it faster and stable. But for now, it will be continue as it is.
* Fixed issue with rules were not applied after system reboot for couple of devices.
* Fixed issue with custom rules were broken completely.
* Fixed issue with Notification icon size is huge.
* Fixed Force Close of some devices when alert message is displayed.

Version 1.1.5
* New Busybox binary (atleast I feel little faster loading on logs ) compiled from latest busybox source . This is packed with handpicked additional and useful busybox commands which will be used in the future versions of AFWall+ to build more advance features ! Stay tuned 
* Fixed issue with widget size 1x1 on newer devices
* Fixed issue with firewall rules not applying before shutdown to prevent leak.
* Fixed Force close on many devices while opening application.
* Fixed Force close on some devices when alert message is displayed.

Version 1.1.4
* Replace su library with RootTools, much faster and stable!
* Improved detection logic for iptables for ICS/JS devices and removed EXPERIMENTAL option from preferences.	
* Now disable icons will free up space on the main view
* Added option to show UID for applications ( like DroidWall )
* Fixed Issue with tf201 devices with su permisssions.
* Fixed constant force close on some devices while applying rules.
* Fixes issue with packages reset to root when importing.
* Improved Russian Translations - Many thanks to Kirhe@xda!
* Fixed issue with custom script not applying properly after uid (github issue #89)
* Removed Disable 3G when USB connected preference because of some bugs.I'll put that back after fixing it.


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

-------------------------------------



TODO :

-> kernel logs and mysterious behind it !
-> Hardware search key.
-> Store logs in DB for a details reports. This will surely help.
-> iptables builder ( like blocking websites/ipaddress etc.,)
-> Application size < 1MB 
-> Rewrite the import/export logic
-> Timer for re enable firewall after disable.
