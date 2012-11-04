Changelog AFWall+
=============

-------------
Download links are right here on the ["Downloads" front page](https://github.com/ukanth/afwall/downloads).


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
