AFWall+ Changelog
==================

Version 3.4.0
* Feature: Bluetooth,USB tethering as separate rules - Thanks @nxzero
* Feature: Clone columns (Copy rules from one column to other column)
* Feature: Selectable Log target (LOG/NFLOG)
* Fix: Log related issues 
* Fix: Notification related issues/Option removed!!
* Main screen UI update - Thanks @vvimjam
* Crash fixes and performance improvements
* Translation updates.


Version 3.4.0-BETA2
* Main UI update only when more than 4 controls
* Notification related fixes
* UI related fixes.
* Crash fixes

Version 3.4.0-BETA1
* Feature: Bluetooth,USB tethering as separate rules - Thanks @nxzero
* Feature: Clone columns (Copy rules from one column to other column)
* Feature: Selectable Log target (LOG/NFLOG)
* Main screen UI update - Thanks @vvimjam
* Crash fixes and performance improvements

Version 3.3.1

* Bug: Firewall not blocking on oneplus devices
* Minor UI fixes

Version 3.3.0

* Bug Fixes:
   - MAJOR: Rules not applied properly (v4/v6)
   - Theme related bug with logs
   - Log related bugs on many devices
   - Boot rules are not applied on Pie and above
   - Disable/Enable of firewall issue
   - Crash on applying rules on fewer devices
   - Notification bug on fewer devices

Version 3.2.0
* Integrate basic themes (Dark/Light or Black (donate version only!)
* Preferences now showing selected values
* Updated libraries
* Bug Fixes:
  - AFWall's Logservice stops after sometime 
  - Crash on LOS 16 due to permission
  - Notification not getting cleared upon opening
  - Tasker settings not applied bug due to crash
  - Root progress showing on main screen (dismiss button added incase)
  - Log service process bug
  - Applying dialog issue
  - kernel in the logs even whitelisted
  - Statusbar notification update on firewall status
  - Possible memory leaks in async
  - mDNS and CLAT on core apps (Android 10)
  - Additional startup leak path for supersu


Version 3.1.0
* Performance: IPv4 & IPv6 rules applying time reduced by half
* IPv6 is enabled by default - Disable if not (under preferences -> rules)
* Fix: Tasker plugin issue after profile migration
* Fix: Widget crashing issue
* Fix: New installed app notification issue
* Fix: Device boot rules issue
* Fix: Duplicate app issue on oneplus devices 
* Fix: Tor related bug
* Fix: Xposed module unable to download allowed apps

Version 3.0.4
* Fix: Domain names are now been correctly resolved
* Fix: Removed notification dot on all notifications
* Fix: Inbound option caused AFWall+ to disable its functionality
* Fix: Hang issue on log detail when ping/resolve
* Removed: SUPER_USER permission which is not relevant anymore

Version 3.0.3

* Fix: Disable firewall issue
* Fix: Traffic stats always zero in app details
* Rewritten: Filter logic for main screen apps
* Xposed: Plugin wasn't able to read preference


Version 3.0.2
* Fix: Issue with Pixel C devices
* Support for Magisk 18.0 and startup leak
* Fix: Notification sound issue on some devices
* Fix: Duplicate name appears on main screen


Version 3.0.1

* Fix: Status toggle widget 1x1 
* Fix: Ability to hide ongoing notification (Stop firewall and restart to hide after disable it in preferences)
* Fix: Firewall error notification on oreo and above
* Security: Tile toggle checks for password
* User reported crashes
* Updated translations   

Version 3.0.0

Features:
* Better support for nougat/oreo and pie
* Firewall toggle tile
* Adaptive Icons
* Notification channels
* Tor support
Bugs:
* Language selection bug
* Filter selection bug
* Compatible with magisk 17.x
* Better handling of background process
* Drops support for 4.x devices
* Update languages
* Updated libraries

Version 2.9.9

* Support for dual apps (experimental)
* DNS Hostname option on log toast(donate feature)
* Multiple memory leaks across screens
* Block log notification now moved under individual app detail screen
* Enabled log cache for faster load
* Widgets now ask for password if enabled (except status widget)

Bugs:
* Shortcut open rules & preference screen without password
* fingerprint related issues
* selinux deny for startup script
* User reported crash fixes
* Updated string translations
* Added default system language option
* Log notification name issue

Version 2.9.8

* Option to disable notification when applying rules - Recommended to turn on !
* Added magisk related information in the error report
* Fixed storage permission on export from rules
* Pixel 2 bug on netfilter error on start
* Rare preference crash on some devices
* Upgraded runtime to Java 8
* Updated support libraries
* Fixed crashes upon loading
* Fixed user reported bugs
* Removed buggy quick apply -- Sincere Apologies! will add it after testing with various usecases

Version 2.9.7 

Features:
* Control default chains for IPv4 & IPv6 (preference)
* Quick apply from main UI - using floating apply button - Donate Version Only
* Showing rules count on apply
* Search using UID
* Improved detection for su binaries
* Ability to choose init.d path
* Removed storage permission from start (used only when export/import)
* Xposed plugin updated to Nougat

Bug Fixes:
* Added mount applet for busybox to fix mount issue for init.d
* Widgets & tasker toggle issue for profiles
* Possible fix for starup rule
* Library updates
* Out of memory crashes when enabled logging
* Runtime crash fixes due to incompatible libraries
* Lots of minor bug fixes and underhood changes to introduce new features


Version 2.9.6.1

* Removed BIND_ADMIN permission and related device admin feature as per Google - Will be introduced after sometime
* ANR issues due to busybox detection
* Minor UI changes in tasker plugin
* Tasker plugin related bugs related to disable/enable firewall
* User reported crash fixes 


Version 2.9.6
* LAN/Data leak issues on 7.x
* Application list refresh issue
* Minor fix in startupscript - Thanks Peake
* Updated support libraries
* Reported crash fixes
* Updated translations

Version 2.9.5
* Mobile data support for newer devices
* Custom interval for startup delay
* Custom interval for ping timeout
* Low priority notification 
* Fixed issue with import preference crashes Log/Experimental preference
* User reported crashes 

Version 2.9.4
* Fix log notification stops after sometime
* Fix crash on log preferences.
* mDNS notification can be turned-off
* Bug in notification filterering
* Added back x86/mips support for built-in binaries
* Minor UI change for UID
* Updated support Libraries
* Updated translations

Version 2.9.3 [24 Feb, 2017]
* Fix crash on log preferences.
* Traffic stats always shown empty.
* Zoom icons.

Version 2.9.2 [22 Feb, 2017]
Features
* Log- Network options-Donate(TX @vzool)
* Fingerprint support (TX @vzool)
* Preserve Zoom size(+/-)
* Profiles-delete/rename profiles(+) 
* Hardware search key

Bugs
* LAN issue on Nougat
* Boot rules issue
* Logservice start issue
* Notification glitch on profile switch
* Log notification filter button not showing 
* Optimizing logTarget detection
* Removed toybox check due to ANR 
  + etc.
Misc
* Updated: Busybox to latest version
* Removed: x86 & mips support
* Updated libraries
* Translation: Small updates

Version 2.9.1 [Nov 27, 2016]
* Boot/Connectivity change rules hung on some devices
* Widget always display errors
* Optimized number of su calls
* Log Service does not work on some devices (Reboot or enable/disable logservice after install)
* Update material dialog library
* Updated translations

Version 2.9.0 [Nov 25, 2016]
* Bug: Sometimes connection gets blocked
* Bug: Sometimes logservice does not work after service terminated
* Bug: Hang issue with phh super user
* Bug: Applying rules dialog issue
* Bug: Old logview clear issue
* Ground work on notification filter
* Code cleanup and removed older API related reference
* Accessibility improvements
* Feature: Block IPv6 - block all default chains of IPv6 to prevent leak
* Feature: Preference enhancements related to rules
* Feature: Application Shortcuts - Nougat 7.1
* User reported crashes
* Updated Translations

Version 2.8.0 [Nov 8, 2016]
* Bug: Boot rules are not working on some devices
* Bug: Logs without active rules issue
* Bug: Rules are blocked completely on some devices
* Bug: Removed notification while applying rules
* Feature: New UI menu icons to support themes 
* Upgraded support library to API 25
* User Reported crash fixes
* Updated Translations

Version 2.7.0 [Nov 1, 2016]
* Bug: Random hang issue with various superuser (phh,cm root)
* Bug: Added option to add delay applying rules on startup
* Bug: Minor fixes on preferences on change
* Bug: Fix for newly installed app on top on few scenarios
* Bug: Startup script related bugs
* Bug: Possible tether issue from last version
* Request: Extra space on notification text! It took lot of time.
* Fix: User reported crash fixes
* Updated: Translations

Version 2.6.0.1 [Oct 1, 2016]
* Bug: Frequent crash while logservice enabled
* Bug: Persistent notification not shown after reboot
* Translation updates

Version 2.6.0 [Sep 28, 2016]
* CRITICAL: Connection leak when LAN option is enabled
* CRITICAL: Multiple su process (su leak) when logservice enabled
* Feature: Nougat Support
* Feature: Split screen of Activity (Nougat+)
* Feature: Ability is use device pin/password/+ for app using Android API (Donate)
* Feature: Ability to fetch logs using busybox/toybox/system
* Feature: Improve auto apply rules for specific preference changes
* Using NDK10 for building binary
* UI: Log toast with warning icon
* Bug: Improved log storing logic to avoid cpu/battery/hang/crash issues
* Bug: Connectivity change hang notification issue
* Bug: Xposed related and user reported fixes
* Reduced overall APK size


Version 2.5.2 [Aug 14, 2016]
* New Feature: Auto-trim log database
* Enhancement: Logs should load much faster
* Change: Removed lock screen notification from Xposed module, since it was draining battery
* Bug Fix: Fix switch log view bug
* Bug Fix: Fix crash on export and import
* Update: Translation updates

Version 2.5.1 [Aug 10, 2016]

* Enhancement: Added Switchback option to Old Log view - Listen to users
* New Feature (Pro): Donate / Donate Key users can view the ipaddress/src/dst in details view (clicking on from new logview)
* Bug fix: Fixed iptables entries for uninstalled apps not resetting

Version 2.5.0 [Aug 9, 2016]
* Features
    - Xposed module - Download manager leak with notification - Application can bypaas AFWall+ by using Download Manager API to download from network. This module helps to block applications from using this API to get around not being allowed to access the internet with AFWall+
    - New Log UI with History - Log service now stores the blocked information in database. Current UI only shows how many times its blocked. Future versions will have more details screens with all ipaddress with DNS Lookup. Also you can start blocking ipaddress directly from Logs in future versions 
    - Webview filter (applist) - Another possible way apps can use webview to access internet. So now there is a separate system level application for webviews. Please whitelist/blocklist this app accordingly.
    - Xposed module - Hide lockscreen notification - This will hide ongoing notification in lockscreen. Due to android restrictions it uses Xposed to hide it
    - Log toast position - Now you can customize the position of app block notification
    - Toybox support (system level) - CM13/12 and even stock android uses toybox instead of busybox. If AFwall+ does not find busybox, it will look for toybox. If toybox not found, it will use built-in busybox.
* Bugs Fixed
    - su leak issues - This issue was related to log service was not able to close properly.
    - Random block issue - Now by default AFWall+ sets all default chains to ACCEPT state
    - Fix toast related crashes
    - All Log related issues/Removed klogripper - klogripper was causing lot of issues on multiple devices. its been replaced with stock dmesg.
    - Widget crashes,bug in app lock
    - Fix rules export issue
    - Kingroot issue - AFWall+ removes the chains used by kingroot now. So, kingroot should not be able to use internet if blocked by AFWall+
    - Improved init.d/su.d related bugs - ipv6 support is improved now, Thanks to F-i-f
* UI
    - Rearrange preferences - Language now moved to new group along with Tasker & Xposed module settings.
    - Added legends - More detail about the icons used by AFWall+ 
    - Firewall mode - Since the dropdown was not visible on multiple devices, its moved to ActionBar
    - Helper notification on preference change - Some preference changes require repply of firewall rules (DNS for example). AFWall+ notify the user when those changes happen
* Misc
    - Updated support libraries
    - Updated Translations and cleanup - Huge thanks for Gitoffthelawn
* Thanks to F-i-f and Gitoffthelawn

Version 2.2.3 [Mar 15, 2016]
* Allow kingroot users to continue with warning message until I find proper solution for kingroot problem.
* AFWall+ will now show in recent apps list 
* Removed highly experimental feature added in the last version for now.
* Reported crash issues
* Updated Translations

Version 2.2.2 [Mar 11, 2016]
* Fix: Issue with auto IPv6 from preference
* Fix: afwall su.d script removal on uncheck preference & Added support for systemless su
* Fix: additional steps to kill klogripper process.
* Disable AFWall+ if KingRoot is detected. AFWall+ will no longer work with KingRoot, see here for more details: https://github.com/ukanth/afwall/issues/501 
* Added highly experimental feature - Keep only AFWall+ chains on connectivity change.
* Updated translations.

Version 2.2.1 [Mar 3, 2016]
* Fix: Delete su.d script if unchecked from preference
* Fix: Startup hang issue while applying rules
* Fix: Widget size issue 
* Added missing translation for Romania - Thanks to @ASebastian/mysterys3by

Version 2.2.0 [Feb 27, 2016]
* Auto IPv6 support
* Support for Android 6 runtime permission (external storage - import/export)
* Widget alignment issue - Support to manual adjustment
* Device startup rules with 3 sec delay to apply rules properly on some devices
* Fixed random disable issue of firewall
* Fixed random complete blocking issue of firewall
* su.d support(supported by supersu and alternate for init.d to prevent startup leak)
* Mobile data issue for new devices
* Logs should show for more devices
* Application not shown after search completed
* Fixed sort option position on start
* Additional check for netfilter support on startup
* Added additional information for error report for better understanding
* Initial support to store logs to db for history. New UI will be in the next version with History.
* Fixed lot of reported crash issues
* Fix: Language not switching for few languages
* Updated Translations with new language support (pt-BR)
* Library updates: androidlockpattern,material dialogs

Version 2.1.3 [Nov 24, 2015]
* Fix: Missing data Interfaces for new devices
* UI: Sort option as radio button
* UI: New Languages (Catalan/Bengali)
* Minor UI Improvements and About/FAQ link click issue
* Fixed: Reported crashes fixed
 
Version 2.1.2 [Oct 6, 2015]
* UI: Sorting now in main page
* UI: Main screen width issue
* Possible fix for Widget alignment(not tested!)
* Reported Crash fixes 
* UI: Status icon fix for Lollipop
* Added proper widget preview 
* UI: Profile switch Bug (change of profile)

Version 2.1.1 [Sep 28, 2015]
* FIX: Rules not saving when profiles are used.

Version 2.1.0 [Sep 26, 2015]
* Fixed rules not saving on some devices 
* Revert Filter from dropdown to radio (UI)
* Kingo superuser issue
* Menu key doesn't work (UI)
* User reported crashes and bug fixes
* lock pattern improvements (UI)
* Additional checks for system busybox
* Log service process leak fix.
* Widget alignment issue on some devices
* Fix F-Droid builds using NDK-r10e

Version 2.0.0 [Sep 7, 2015]
* Initial Material design
* Support for 5.x Lollipop
* Revamped UI (pull to refresh), Preferences and Icons
* Enhanced security password protection
* Enhanced Import/Export with File Picker
* New Profile Management
* Performance Improvements and optimizations
* Experimental - Added sort apps by uid/install date
* Added/Updated Translations. 
* And lots of changes...
 
Version 1.3.4 [Aug 3, 2014]
* Feature: Added permanent notification on firewall status (optional)
* Bug: Modified init.d script to support system iptables
* Bug: Fixed FC on multiple devices when enable/disable
* Bug: Fixed issue with widget when password protected
* Minor widget enhancement for old android devices
* Updated Translations

Version 1.3.3 [Jul 18, 2014]
* Added export & import for preferences/profiles including custom profiles (Donate version only)
* Custom Script for each Profile
* New combined dialog for import and export
* Encryption for application password - Also resets the old passwords. Please set password again!
* Fix for LogService FC issues on 4.4
* Fix for new apps not showing on top when profiles are enabled
* Fix for Possible SU leak
* Improved notification text
* Improved search filter/profile validation logic
* Updated libs

Version 1.3.2 [May 31, 2014]
* Added back the old profile switch widget till the new widget gets stable.
* Fix: process leak with log and nflog service. Please do a clean install if it does not work after update.
* Fix: filter application's not working for block notifications.
* Fix: multiple tasker issues (profile 2 applies - profile 3 and rules are not applying when using tasker)
* Fix: profile status not getting reflected on main view when changed using tasker/widget
* Fix: new widget not applying rules properly for profiles.
* Fix: Import rules fails when package not found.
* Fix: User reported NPE & Force close issues.

Version 1.3.1.1 [Apr 25, 2014]
* Revert Target SDK to 16 to fix issue with boot rules

Version 1.3.1 [Apr 23, 2014]
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

Version 1.3.0.2 [Apr 3, 2014]
* Bug fixes on 1.3.x 

Version 1.3.0.1 [Apr 1, 2014]
* Old toggle widget is back - Hate you guys :)
* "Allow All Application" option is back - Again hate you guys :)

Version 1.3.0 [Mar 30, 2014]
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

Version 1.2.9 [Feb 8, 2014]
* Feature: Column level select/invert/unselect
* Feature: New Import/Export (with backward compatiblity)
* Feature: Filter by All/Core/System/User applications
* BugFix: Fixed issue with Multiuser iptables rules
* BugFix: Fixed issue with Tasker plugin (enable/disable/Profile switch)
* UI: Revamped About and added FAQ page.
* User reported bug fixes.

Version 1.2.8 [Jan 19, 2014]
* Traffic stats + App detail View (Long press on App Label) Note: A minimal stats and not a complete statistics of traffic details.
* Add/Remove Additional Profiles
* Multiuser support for Tablets (Experimental)
* Custom rules file support (. /path/to/file) 
* Fixed VPN issue with KitKat & Updated libsuperuser library
* Many minor UI enhancements and performance improvements
* Bug Fixes: #154, sdcard mount on startup, user reported crash fixes 

Version 1.2.7 [Nov 23, 2013]
* Improved search functionality & select confirmation.
* Added built-in ip6tables support
* Support for x86/MIPS/ARM devices.
* Built-in iptables is upgraded to the latest version.
* Various user reported crash/bug fixes.
* Build scripts updated for F-Droid and developer friendly builds (ant)
* Added Hungarian/Turkish Translations and updated other translations

Version 1.2.6.2 [Sep 9, 2013]
* NGLog fixes for various devices including nexus.

Version 1.2.6.1
* LOGS should work now on newer devices which has NFLOG chain.
* New RootShell Service to keep AFWall+ active.
* RootShell - Retry on exit 4 while switching from 3G to WiFi or viceversa.
* Removed alternate startup service from experimental- It works without it.
* Improved Caching Logic, Uninstall apps will now remove cache along with rules.
* Reload applications will remove unused cache.
* New Help page - Added another developer (@cernekee)
* Fixed too many prompts when password is enabled.
* Rewritten logic to detect LOG chain.
* Added "error send report" option to help beta testers with more diagnostics information. 
* Special UID for DNS Proxy and NTP (4.3 users)
* Added support for custom rules from 1.2.5.2 (it was broken because of afwall iptables chain change)
* Better tether status check
* Keep alive RootShell on some devices. 

Version 1.2.6 [Aug 16, 2013]
* Lots of Refactor to bring stability and performance. Fixed many issues along with it
  (HUGE THANKS TO cernekee!) 
  New option -> Now enable/disable log from preferences
* New option -> Apply rules on Switch Profiles
* New option -> Active Rules is now optional (But required when using LAN/Roaming)
* New option -> Enable inbound connections (Required for sshd, AirDroid etc)
* New busybox binary for ifconfig
* Rules Log has more information 
* NFLOG support for newer devices

Version 1.2.5.2 [May 30, 2013]
* Fixed issue with Wifi blocked on whitelist for couple of devices

Version 1.2.5.1
* Improved search functionality.
* Frequent connectivity change rules will now work only on roaming/lan. this should reduce the number of 
  superuser prompts and reduce lag on some devices.
* Fixed issue with application list not showing.
* Fixed issue with logs where it used to work before.
* Fixed issue with default language (default is set to English, please change it in preference)
* Added translations.

Version 1.2.5 [May 25, 2013]
* Added Tether support. (Thanks to cernekee)
* Added LAN/WAN support. (Thanks to cernekee)
* Added Import from DroidWall (from Donate Version!)
* Fixed issue with special applications not showing in different color(system apps) (Thanks to cernekee)
* Fixed issue with preferences for defauly system application picker (Thanks to cernekee)
* Fixed issue with Language preferences default (Thanks to cernekee)
* Lots of code refactor/bug fixes (Thanks to cernekee!)
* Fixed issue with multiline in search text.
* Minor UI changes on the application list.
* Added selectable iptables/busybox binary
* Added new translations (Chinese, Greek, more)

Version 1.2.4.1 [Apr 27, 2013]
* Fixed issue with cleanup afwall rules on disable
* Fixed issue with OUTPUT chain not removed for afwall on disable

Version 1.2.4 (bump version to match Donate version) [Apr 23, 2013]
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
* Added translations for Greek and Produguese languages. 

Version 1.2.1 [Mar 11, 2013]
* Minor issue fixed for "Media Server" not apply properly after reboot
* Fixed iptables rules which breaks wifi/Mobile data limit.
* Updated translations for German/Chineese
* Added Swedish Translation - Many Thanks to CreepyLinguist@Crowdin

Version 1.2.0 [Mar 3, 2013]
* Added change app language from the preferences (default is system lang)
* Added device admin feature - Extra protection to AFWall+, so that it can't be uninstalled from any other app.
* Added Tasker/Locale plugin (from donate version) with bug fixes.
* Added VPN Support (enable/disable it preferences) - Tested with DroidVPN and works fine!
* Added new widget with quick toggle (enable/disable/profiles)
* Added option to import from DroidWall (only for Donate version for now!)
* Added Active defense (Make sure only AFWall+ able to control the internet) - Not optional!
* Added new super user permission ( koush's superuser permission)
* Added ability to enable/disable roaming feature
* New logic to apply rules - Performance improvement 
* Removed deprecated API's for Notification. Going forward this will be improved for ICS/JB 
* Improved preferences - Added summary for each preferences and rearranged order
* New menu icons (white icons !)
* Removed all inline style alert messages and alert boxes. Now it just display toast messages.
* Fixed data leak on boot for devices REQUIRES init.d support/S-OFF (enable it in preferences - EXPERIMENTAL!)
(to enable init.d support use this app -> https://play.google.com/store/apps/d...k.initdtoggler)
* New log rule to get the logs from dmesg and enable logs by default 
* Enable/Disable logs now from "Firewall Logs" menu.
* Fixed issue with iptable rules are not applying after reboot, mainly CM 10.1 devices (Enable it in preferences - EXPERIMENTAL !)
* Various UI glitches in multi profiles/icons & UID
* Fixed hang/rules issue on startup 
* Fixed issue with profiles where the default profile is applied after restart instead of selected one.
* FC issue when using app menu (ActionBarSherlock - NPE)
* Fixed issue with Media Server/VPN not applying properly.	
* Simplified Chinese Translation - Many thanks to wufei0513 & tianchaoren@Crowdin
* Czech Translation - Many thanks to syk3s@Crowdin
* Turkish Translation - Many thanks to metah@Crowdin
* Ukrainian Translation - Many thanks to andriykopanytsia,igor@Crowdin	

Version 1.1.9 [Jan 23, 2013]
* Added invert selection for apps (useful when switching whitelist <-> blacklist)
* Fixed issue with special apps (root/shell/media server) not applying 
* Fixed issue with new lockpattern not working properly.
* Added MDPI images for icons.
* Code cleanup, mainly strings.xml (removed version from strings.xml etc.)

Version 1.1.8
* Fixed FC on new lockpattern

Version 1.1.7
* Added lockpattern (you can still use the old style password protection) with SHA1 protection
* Fixed force close issue while adding system apps.
* Fixed issue with select All/none. it wroks properly and doesn't require scroll. Thanks to Pragma!
* Significant improvements while loading applications (hope not a placebo)
* Fixed issue with search case sensitive and expand search will show the keyboard (no more two press!)
* Disable notification when the firewall is disabled.
* Added new language translations
  - Spanish translation by spezzino@crowdin
  - Dutch translation by DutchWaG@crowdin
  - Japanese translation by nnnn@crowdin
  - Ukrainian translation by andriykopanytsia@crowdin

Version 1.1.6 [Jan 3, 2013]
* Back to Chainfire's SU library. More stable but little slower compare to RootTools. Performance will be improved going forward.
  I'm planning to rewrite the entire code to make it faster and stable. But for now, it will be continue as it is.
* Fixed issue with rules were not applied after system reboot for couple of devices.
* Fixed issue with custom rules were broken completely.
* Fixed issue with Notification icon size is huge.
* Fixed Force Close of some devices when alert message is displayed.

Version 1.1.5 [Dec 27, 2012]
* New Busybox binary (at least I feel little faster loading on logs) compiled from latest busybox source. This is packed with handpicked additional and useful busybox commands which will be used in the future versions of AFWall+ to build more advance features! Stay tuned.
* Fixed issue with widget size 1x1 on newer devices
* Fixed issue with firewall rules not applying before shutdown to prevent leak.
* Fixed Force close on many devices while opening application.
* Fixed Force close on some devices when alert message is displayed.

Version 1.1.4 
* Replace su library with RootTools, much faster and stable!
* Improved detection logic for iptables for ICS/JS devices and removed EXPERIMENTAL option from preferences.	
* Now disable icons will free up space on the main view
* Added option to show UID for applications (like DroidWall)
* Fixed Issue with tf201 devices with su permisssions.
* Fixed constant force close on some devices while applying rules.
* Fixes issue with packages reset to root when importing.
* Improved Russian Translations - Many thanks to Kirhe@xda!
* Fixed issue with custom script not applying properly after uid (github issue #89)
* Removed Disable 3G when USB connected preference because of some bugs. I'll put that back after fixing it.

Version 1.1.3 [Dec 20, 2012]
* Critical bugfix: Rules were not applied after every system reboot!

Version 1.1.2
* Minor bug fix for Forceclose on alerts!

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
* i18n : Added russian language support (Thanks: Google translator toolkit)
* and many small fixes

Version 1.1.0 [Dec 7, 2012]
* Initial Play Store Version

Version 1.0.7a
* New icon for AFWall+ (Thanks for hush66!)
* Multiple Profiles (Currently limited to 4)
* Added support for Epic 4G Touch (Thanks to JoshMiers!)
* Unified Preferences (https://github.com/saik0/UnifiedPreference/)
* Translations added: French and German
* Fixed multiple menu on ActionBar with staticbar ( no more two menu items on some devices )
* Enable/Disable logs now moved to preferences and log menu will be hidden if disabled
* Bug Fix: Update of application packages will not be notified with AFwall.
* Bug Fix: Uninstalling app will reset rule for root application to default.

Version: 1.0.6.1a [25 Nov 2012]
* Bug Fix : Rules for spcial application were not applied after application restart.

Version: 1.0.6a [25 Nov 2012]
* Now uses Chainfire's SU library, This will get rid of old shell script approach. I feel it's faster and better approach and helps to enable profiles!
  Please Note: If you use afwall.sh outside, starting this version it will not work!
* Improved menubar and confirmation dialogs
* Fixed bug with logging 
* Fixed bug on some ICS/JB devices
* Added new EXPERIMENTAL option for ICS/JB devices (uses extra rules)
* Enabled fast scrolling on lists (main list)
* German Translation (Thanks to CHEF-KOCH!)
* Support for 4.2 JB

Version 1.0.5a [10 Nov 2012]
* Enhanced Rules view with additional actions like copy, flush, export to sdcard and network interfaces.
* Moved flush rules from main menu to enhanced rules view
* Enhanced Log view with additional copy & clear action
* Moved clear log from main menu to enhanced log view
* Fixed FC issues from 1.0.4.x

Version 1.0.4.1a [9 Nov 2012]
* Fixed force close on viewlog and view rules pages.

Version 1.0.4a [9 Nov 2012]
* Import/Export Rules (for now it's just a single import & export to external storage)
* Integrated search bar (application search)
* Revamped Log & IPTables rules view (you can now view the logs and rules in a clear view and copy them!)
* Added reenter password confirmation dialog.
* Added additional ifaces to support more devices (working on another solution which will identity interfaces on the particular device)
* Fixed force close when scrolling for some devices
* And more! 

Version 1.0.3a
* Fix for some apps can "bypass" the firewall by just using UDP port 53. Disable port 53
* Added 3g ifaces to support more devices (should solve issues with firewall for some devices)
* Fixed Widget on/off issue (First enable firewall and then add the widget will do the trick!)
* Fixed Widget size for 4.0+ devices
* Prepared for i18n Support
* Prepared support for XHDPI devices
Note: If you have any issue, please clean the rules via menu and apply again.

Version 1.0.2a (Please note, if you upgrade from 1.0.1a, rules will be reset!)
* Roaming Option (not tested !)
* Added Shutdown broadcast and applied rule to block all the connections (this should solve the leakage 
  when phone is rebooted/started before AFwall+ can start!!!) - Not tested!
* Added option to disable application icon (faster loading on slow devices)
* Added option to disable 3g rules when connected via Wired USB (Droidwall issue)
* Added support for more ifaces for 3G (support multiple devices)
* Added clear Rules option in menu (now the iptables will be saved as afwall-3g,afwall-wifi, to solve the issue when both Droidwall & AFWall+ installed )
* Fixed bug in reload applications
* Fixed bug in applying rules in clear/select all
* Fixed the issue with save/discard rules when press back button.

Version 1.0.1a
* Improved install notification (only notify when app has internet permission)
* Select All Wifi / 3G or Clean All option! (HUGE FIX) - No Invert select this time. just click on the 3G/WiFi icons will do the trick!
* Fixed dangerous file permissions issues (reported in original Droidwall as an issue)

Version 1.0.0a
* Initial release based on [DroidWall](http://code.google.com/p/droidwall/) 1.5.7
* ICS style menubar and theme
* New install notifications
* New preferences options
* Force reload Applications
* Highlight System applications using custom color from preferences
