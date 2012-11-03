Changelog
======

Version 1.0.2a
* Roaming Option ( not tested !)
* Added Shutdown broadcast and applied rule to block all the connections ( this should solve the leakage 
  when phone is rebooted/started before afwall can start !!!) - Not tested !
* Added option to disable application icon ( faster loading )
* Added option to disable 3g rules when connected via Wired USB (droidwall issue)
* Added support for more ifaces for 3G ( support multiple devices )
* Added clear Rules option in menu (now the iptables will be saved as afwall-3g,afwall-wifi, to solve the issue when both droidwall & afwall installed )
* Fixed bug in reload applications
* Fixed bug in applying rules in clear/select all
* Fixed the issue with save/discard rules when press back button.
* [Download](http://forum.xda-developers.com/attachment.php?attachmentid=1450085&d=1351827052)


Version 1.0.1a
* Improved install notification( only notify when app has internet permission )
* Select All Wifi / 3G or Clean All option ! ( HUGE FIX ) - No Invert select this time. just click on the 3g/wifi icons will do the trick !
* Fixed dangerous file permissions issues ( reported in original droidwall issue )
* [Download](http://forum.xda-developers.com/attachment.php?attachmentid=1435715&d=1351366366)


Version 1.0.0a
* Based on DroidWall 1.5.7
* ICS style menubar and theme
* New install notifications
* New preferences options
* Force reload Applications
* Highlight System applications using custom color from preferences
