Changelog AFWall++
==================

Version 1.3.4
[Bugs]
* Modified init.d script to support system iptables.
* Fixed FC on multiple devices when enable/disable.
* Fixed issue with widget when password protected.
[Enhancements]
* Minor widget enhancement for old android devices.
[Features]
* Added permanent notification on firewall status (optional).
[Translations]
* Updated Translations.


Version 1.3.3
[Bugs]
* Fix for LogService FC issues on Android 4.4.
* Fix for new apps not showing on top when profiles are enabled.
* Critical fix for possible SU leak.
[Enhancements]
* Improved notification text.
* Improved search filter/profile validation logic.
* Updated libs.
[Features]
* Added export & import for preferences/profiles including custom profiles (Donate version only!)
* Custom Script for each Profile.
* New combined dialog for import and export.
* Encryption for application password - Also resets the old passwords. (Please set password again!)


Version 1.3.2
[Bugs]
* Process leak with log and nflog service. Please do a clean install in case if it doesn't work after update.
* Filter application's not working for block notifications.
* Multiple tasker issues (Profile 2 applies - profile 3 and rules are not applying when using tasker)
* Profile status not getting reflected on main view when changed using tasker/widget
* New widget not applying rules properly for profiles.
* Import rules fails when package not found.
* User reported NPE & Force close issues.
[Features]
* Added back the old profile switch widget till the new widget gets stable.
[Translations]
* Updated german translation


Version 1.3.1.1
[Bugs]
* Revert Target SDK to 16 to fix issue with boot rules


Version 1.3.1
[Bugs]
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
[Features]
* Added experimental filter for block notifications.


Version 1.3.0.2
[Bugs]
* Bug fixes on 1.3.x 


Version 1.3.0.1
[Enhancements]
* Old toggle widget is back - I hate you guys :)
* "Allow All Application" option is back - Again I hate you guys :)


Version 1.3.0
[Bugs]
* Fixed application list load performance issues
* User reported bug fixes included in the Tasker-Plugin
* Fixed bug in preferences
[Enhancements]
* Updated lockpattern - stealth mode/max retry count
[Features]
* New Widget with support for multiple profiles (single widget)
* DNS Proxy to Preferences (By default UDP 53 will be blocked on <4.3)
* More Log information (PORT/PROTOCOL)
* Support for Wifi-only tab (auto hide data column)
* Block packet notification (exp!) - Log service
* New Icon
[Translations]
* Translations updated - Indonesian (thx mirulumam)


Version 1.2.9
[Bugs]
* Fixed issue with Multiuser iptables rules
* Fixed issue with Tasker plugin (enable/disable/Profile switch)
* User reported bug fixes.
[Features]
* Column level select/invert/unselect
* New Import/Export (with backward compatibility)
* Filter by All/Core/System/User applications
* UI: Revamped About and added FAQ page.


Version 1.2.8
[Bugs]
* Fixed VPN issue with KitKat & Updated libsuperuser library
* Many minor UI enhancements and performance improvements
* Bug Fixes: #154, sdcard mount on startup, user reported crash fixes
[Features]
* Traffic stats + App detail View (Long press on App Label) Note: A minimal stats and not a complete statistics of traffic details.
* Add/Remove Additional Profiles
* Multiuser support for Tablets (Experimental)
* Custom rules file support (. /path/toyour/file)


Version 1.2.7
[Enhancements]
* Improved search functionality & select confirmation.
* Build scripts updated for f-droid and developer friendly builds (ant).
[Features]
* Added builtin ip6tables support
* Support for x86/MIPS/ARM devices.
* Built-in iptables is upgraded to the latest version 1.4.20.
* Various user reported crash/bug fixes.
[Translations]
* Added Hungarian/Turkish Translations and updated other translations


Version 1.2.6.2
[Bugs]
* NGLog fixes for various devices including nexus.


Version 1.2.6.1
[Bugs]
* LOGS should work now on newer devices which has NFLOG chain.
* Fixed too many prompts when password is enabled.
[Enhancements]
* RootShell - Retry on exit 4 while switching from 3G to WiFi or viceversa.
* Improved Caching Logic, Uninstall apps will now remove cache along with rules.
* Reload applications will remove unused cache.
* Removed alternate startup service from experimental - It works without it.
* Rewritten logic to detect LOG chain.
* Special UID for DNS Proxy and NTP (< 4.3 users).
* Better tether status check.
* Keep alive RootShell on some devices. 
[Features]
* New RootShell Service to keep AFWall++ active.
* New Help page - Added another developer @cernekee)
* Added support for custom rules from 1.2.5.2 ( it was broken because of AFWall+ iptables chain change)
* Added "error send report" option to help beta testers with more diagnostics information. 


Version 1.2.6
[Bugs]
* Lots of Refactor to bring stability and performance. Fixed many issues along with it (HUGE THANKS TO cernekee!) 
[Enhancements]
* Rules Log has more information 
[Features]
* Now enable/disable log from preferences
* Apply rules on Switch Profiles
* Active Rules is now optional (But requires when using LAN/Roaming)
* Enable inbound connections (Required for sshd, AirDroid etc)
* NFLOG support for newer devices
* New busybox binary for ifconfig


Version 1.2.5.2
[Bugs]
* Fixed issue with Wifi blocked on whitelist for couple of devices


Version 1.2.5.1
[Bugs]
* Fixed issue with application list not showing.
* Fixed issue with logs where it used to work before.
* Fixed issue with default language (default is set to English, please change it in preference)
[Enhancements]
* Improved search functionality.
* Frequent connectivity change rules will now work only on roaming/lan. 
This should reduce the number of superuser prompts and reduce lag on some devices.
[Translations]
* Added new translations.


Version 1.2.5
[Bugs]
* Fixed issue with special applications not showing in different color(system apps) (Thanks to cernekee)
* Fixed issue with preferences for defauly system application picker (Thanks to cernekee)
* Fixed issue with Language preferences default(Thanks to cernekee)
* Fixed issue with multiline in search text.
[Enhancements]
* Lots of code refactor and bug fixes (Thanks to cernekee!)
* Code cleanup
* Minor UI changes on the application list.
[Features]
* Added Tether support. (Thanks to cernekee)
* Added LAN/WAN support. (Thanks to cernekee)
* Added Import from DroidWall (from Donate Version!)
* Added selectable iptables/busybox binary
[Translations]
* Added new translations (chinese/greek etc.,)


Version 1.2.4.1
[Bugs]
* Fixed issue with cleanup AFWall+ rules on disable.
* Fixed issue with OUTPUT chain not removed for AFWall+ on disable.


Version 1.2.4(bump version to match Donate version)
[Bugs]
* Fixed issue with custom script hangs.
* Fixed issue with multiple password request (in beta testing)
[Enhancements]
* Improved performance of applying rules and application list.
* Improved application loading progress dialog.
* Show keyboard automatically on password protected dialogs
* Improved detection logic for data leak prevention script (Thanks GermainZ)
* Improved multiple profile performance while loading applications. It will no longer apply rules on switching
 profiles. You need to manually apply rules after profile switch.
[Features]
* Support IPv6 (disabled by default, you can enable it in preferences)
* Tasker support enable/disable of AFWall++ (default disabled)
[Translations]
* Added translations for Greek, Portuguese languages. 
* Improved translations strings.


Version 1.2.1
[Bugs]
* Minor issue fixed for "Media Server" not apply properly after reboot
* Fixed iptables rules which breaks wifi/Mobile data limit.
[Translations]
* Updated translations for German and Chineese
* Added Swedish Translation - Many thanks goes to CreepyLinguist @Crowdin


Version 1.2.0
[Bugs]
* Fixed issue with iptable rules are not applying after reboot, mainly CM 10.1 devices (Enable it in preferences - EXPERIMENTAL !)
* Various UI glitches in multi profiles/icons & UID
* Fixed hang/rules issue on startup 
* Fixed issue with profiles where the default profile is applied after restart instead of selected one.
* FC issue when using app menu (ActionBarSherlock - NPE)
* Fixed issue with Media Server/VPN not applying properly.
* Fixed data leak on boot for devices REQUIRES init.d support/S-OFF (enable it in preferences - EXPERIMENTAL !)
(to enable init.d support fir rooted devices use this app -> https://play.google.com/store/apps/details?id=com.broodplank.initdtoggler)
[Enhancements]
* New logic to apply rules - Performance improvement! 
* Removed deprecated API's for Notification. Going forward this will be improved for ICS/JB 
* Improved preferences - Added summary for each preferences and rearranged order
* New menu icons ( white icons !)
* Removed all inline style alert messages and alert boxes. Now it just display toast messages.
* New log rule to get the logs from dmesg and enable logs by default 
* Enable/Disable logs now from "Firewall Logs" menu.	
[Features]
* Added change app language from the preferences (default is system lang)
* Added device admin feature - Extra protection to AFWall++, so that it can't be uninstalled from any other app.
* Added Tasker/Locale plugin (from donate version) with bug fixes.
* Added VPN Support (enable/disable it preferences) - Tested with DroidVPN and works fine !
* Added new widget with quick toggle (enable/disable/profiles)
* Added option to import from DroidWall (only for Donate version for now !)
* Added Active defense ( Make sure only AFWall++ able to control to internet) - Not an optional !
* Added new super user permission (koush's superuser permission)
* Added ability to enable/disable roaming feature
[Translations]
* Simplified Chinese - Thanks to wufei0513 & tianchaoren@Crowdin
* Czech Translations - Thanks to syk3s@Crowdin
* Turkish Translations - Many Thanks to metah@Crowdin
* Ukrainian Translations - Many Thanks to andriykopanytsia,igor@Crowdin	


Version 1.1.9
[Bugs]
* Fixed issue with special apps (root/shell/media server) not applying 
* Fixed issue with new lockpattern not working properly.
[Enhancements]
* Code cleanup, mainly strings.xml ( removed version from strings.xml etc.,)
[Features]
* Added invert selection for apps ( useful when switching whitelist <-> blacklist )
* Added MDPI images for icons.


Version 1.1.8
[Bugs]
* Fixed FC on new lockpattern


Version 1.1.7
[Bugs]
* Fixed issue with search case sensitive and expand search will show the keyboard ( no more two press !)
* Fixed issue with select All/none. It works properly and doesn't require scroll. Thanks to Pragma!
* Fixed force close issue while adding system apps.
[Enhancements]
* Significant improvements while loading applications( hope not a placebo ?) 
[Features]
* Added lockpattern ( you can still use the old style password protection ) with SHA1 protection 
* Disable notification when the firewall is disabled.
[Translations]
* Added new language translations:
-> Spanish translations by spezzino @crowdin
-> Dutch translations by DutchWaG @crowdin
-> Japanese translation by nnnn @crowdin
-> Ukrainian translation by andriykopanytsia @crowdin

  
Version 1.1.6
[Bugs]
* Fixed issue with rules were not applied after system reboot for couple of devices.
* Fixed issue with custom rules were broken completely.
* Fixed issue with Notification icon size is huge.
* Fixed Force Close of some devices when alert message is displayed.
[Enhancements]
* Back to Chainfire's SU library. More stable but little slower compare to RootTools. Performance will be improved going forward.
  I'm planning to rewrite the entire code to make it faster and stable. But for now, it will be continue as it is.


Version 1.1.5
[Bugs]
* Fixed issue with widget size 1x1 on newer devices
* Fixed issue with firewall rules not applying before shutdown to prevent leak.
* Fixed Force close on many devices while opening application.
* Fixed Force close on some devices when alert message is displayed.
[Enhancements]
* New Busybox binary (at least I feel little faster loading on logs) compiled from latest busybox source. 
This is packed with handpicked additional and useful busybox commands which will be used in the future versions of AFWall++ to build more advance features ! Stay tuned 


Version 1.1.4
[Bugs]
* Fixed Issue with tf201 devices with su permissions.
* Fixed constant force close on some devices while applying rules.
* Fixed issue with packages reset to root when importing.
* Fixed issue with custom script not applying properly after uid (GitHub issue #89)
[Enhancements]
* Improved detection logic for iptables for ICS/JS devices and removed EXPERIMENTAL option from preferences.	
* Now disable icons will free up space on the main view
* Removed Disable 3G when USB connected preference because of some bugs.I'll put that back after fixing it.
[Features]
* Added option to show UID for applications ( like DroidWall )
* Replace su library with RootTools, much faster and stable!
[Translations]
* Improved Russian Translations - Many thanks to Kirhe@xda!


Version 1.1.3
[Bugs]
* Critical bugfix : Rules were not applied after every system reboot !


Version 1.1.2
[Bugs]
* Minor bug fix for force close (fc) on alerts !


Version 1.1.1
[Bugs]
* Shutdown Custom Rule doesn't work.
* Refresh issue of mode on the multiple profiles switching.
* Fixed two identical profile names on multiple profiles.
* Replaced old style deprecated Thread implementation with AsyncTask.(faster and safer)
* NullPointer exception while reading preferences.
* ... and many small fixes
[Features]
* Tasker/Locate Plugin! (Only for donate version for now)
* Now allow customize names for profiles. (From preferences)
* Replace alert/toasts with appmsg (displays within the app) - enable it in preference
* Initial simple improvements for view logs. It will be improved further!
* Added new preference to enable confirmbox on firewall disable.
[Translations]
* i18n : Completed french/Germen translations.
* i18n : Added russian language support(Thanks : Google translator toolkit )


Version 1.1.0
[Features]
* Initial Version released on Google Play Store


Version 1.0.7a
[Bugs]
* Fixed multiple menu on ActionBar with staticbar (No more two menu items on some devices)
* Update of application packages will not be notified with AFWall+.
* Uninstalling app will reset rule for root application to default.
[Enhancements]
* Enable/Disable logs now moved to preferences and log menu will be hidden if disabled
[Features]
* New Icon for AFWall+ (Thanks for hush66!)
* Multiple Profiles (Currently limited to 4)
* Added support for Epic 4g Touch(Thanks to JoshMiers!)
* Unified Preferences (https://GitHub.com/saik0/UnifiedPreference/)
[Translations]
* Translations added (french and german)


Version: 1.0.6.1a
[Bugs]
* Rules for special application were not applied after application restart.


Version: 1.0.6a
[Bugs]
* Fixed bug with logging 
* Fixed bug on some ICS/JB devices
[Enhancements]
* Improved menubar and confirmation dialogs
[Features]
* Support for 4.2 JB
* Now uses Chainfire's SU library, This will get rid of old shell script approach. I feel it's faster and better approach and helps to enable profiles !
  Please Note: If you use AFWall+.sh outside, starting this version it will not work!
* Added new EXPERIMENTAL option for ICS/JB devices (uses extra rules)
* Enabled fast scrolling on lists (main list)
[Translations]
* German Translation ( Thanks to CHEF !)


Version 1.0.5a
[Bugs]
* Fixed FC issues from 1.0.4.x
[Enhancements]
* Enhanced Rules view with additional actions like copy, flush, export to sdcard and network interfaces.
* Moved flush rules from main menu to enhanced rules view
* Enhanced Log view with additional copy & clear action
* Moved clear log from main menu to enhanced log view


Version 1.0.4.1a
[Bugs]
* Fixed force close on viewlog and view rules pages.


Version 1.0.4a
[Bugs]
* Fixed force close when scrolling for some devices.
[Enhancements]
* Import/Export Rules ( for now it's just a single import&export to External storage)
* Integrated search bar ( application search )
* Revamped Log & IPTables rules view ( you can now view the logs and rules in a clear view and copy them !)
[Features]
* Added reenter password confirmation dialog.
* Added additional ifaces to support more devices ( working on another solution which will identity interfaces on the particular device)


Version 1.0.3a
[Bugs]
* Fix for some apps can "bypass" the firewall by just using UDP port 53. Disable port 53
* Fixed Widget on/off issue ( First enable firewall and then add the widget will do the trick !)
* Fixed Widget size for 4.0+ devices
[Features]
* Added 3g ifaces to support more devices ( should solve issues with firewall for some devices )
* Prepared for i18n Support
* Prepared support for XHDPI devices

Note: If you have any issue, please clean the rules via menu and apply again.


Version 1.0.2a (Please note, if you upgrade from 1.0.1a, rules will be reset!)
[Bugs]
* Fixed bug in reload applications
* Fixed bug in applying rules in clear/select all
* Fixed the issue with save/discard rules when press back button.
[Features]
* Roaming Option added (Not tested!)
* Added Shutdown broadcast and applied rule to block all the connections ( this should solve the leakage 
  when phone is rebooted/started before AFWall+ can start !!!) - Not tested !
* Added option to disable application icon ( faster loading on slow devices )
* Added option to disable 3g rules when connected via Wired USB (Droidwall issue)
* Added support for more interfaces for 3G ( support multiple devices )
* Added clear Rules option in menu (now the iptables will be saved as AFWall+-3g,AFWall+-wifi, to solve the issue when both Droidwall & AFWall++ installed )


Version 1.0.1a
[Bugs]
* Fixed dangerous file permissions issues (reported in original Droidwall issue)
[Enhancements]
* Improved install notification (Only notify when app has internet permission)
* Select All Wifi / 3G or Clean All option ! ( HUGE FIX ) - No Invert select this time. just click on the 3g/wifi icons will do the trick !


Version 1.0.0a
* Initial version
* Based on [DroidWall](http://code.google.com/p/droidwall/) 1.5.7
* ICS style menubar and theme
[Features]
* New install notifications
* New preferences options
* Force reload Applications
* Highlight System applications using custom color from preferences


[Bugs]
List all bugfixes 
[Enhancements]
List all enhancements
[Features]
New feature added? See here.
[Translations]
Translations added or improved, see here.


-------------------------------------


TODO:

-> kernel logs and mysterious behind it !
-> Add hardware search key
-> Store logs in DB for a details reports. This will surely help.
-> iptables builder ( like blocking websites/ip addresses etc.,)
-> Application size < 1MB 
-> Rewrite the import/export logic
-> Timer for re enable firewall after disable
