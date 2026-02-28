# AFWall+ (Android Firewall+)

[![Android CI](https://github.com/ukanth/afwall/workflows/Android%20CI/badge.svg?branch=beta)](https://github.com/ukanth/afwall/actions) [![Crowdin](https://d322cqt584bo4o.cloudfront.net/afwall/localized.png)](https://crowdin.net/project/afwall) ![License](https://img.shields.io/github/license/ukanth/afwall) ![F-Droid](https://img.shields.io/f-droid/v/dev.ukanth.ufirewall) ![Downloads](https://img.shields.io/github/downloads/ukanth/afwall/total) ![Repo Size](https://img.shields.io/github/repo-size/ukanth/afwall)

[![Build AFWall+ Binaries](https://github.com/ukanth/afwall/actions/workflows/build-binaries.yml/badge.svg)](https://github.com/ukanth/afwall/actions/workflows/build-binaries.yml)

> **Your Privacy, Your Control** - AFWall+ gives you complete control over which apps can access the internet on your Android device.

---


## Support AFWall+ Development

AFWall+ is developed and maintained by volunteers. If you find it useful, please consider supporting the project:

### How to Donate

**Why donate?** AFWall+ is free and open-source. Your support helps:
- Continue development and add new features
- Fix bugs and keep the app stable
- Support more Android versions and devices
- Maintain documentation and help the community

**Donation options:**
- **PayPal**: [![Donate](https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=6E4VZTULRB8GU)
- **Google Play**: Purchase the [unlocker key](https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall.donate) for extra features
- **Amazon Gift Cards**: `cumakt+amazon at gmail.com` (not preferred)
- **Bitcoin**: `bc1q54nf3y9zmdcpasxx9sywkprd6309rfhav3mape`
- **Ethereum**: `0x5e65649C2B26eD816fCeD25a8E507C90D4b1D697`

After donating, please send your receipt to contact@portgenix.com to receive an unlocker. Please allow 1-2 days for a response.

### Other Ways to Help
- Star this repository
- Report bugs and test new features
- Contribute translations on [Crowdin](http://crowdin.net/project/afwall)
- Improve documentation
- Help other users in forums

---

<p align="center">
  <img src="https://raw.githubusercontent.com/ukanth/afwall/0502e6f17ceda08069720ff2f260902690e65e9b/screenshots/Main_2.0.png" width="300" alt="AFWall+ Screenshot">
</p>


## What is AFWall+?

**AFWall+ (Android Firewall+)** is a powerful, open-source firewall application for rooted Android devices. Built on Linux's robust `iptables` framework, AFWall+ provides **granular network control** at the system level - something impossible with standard Android permissions.


### Core Purpose
- Block unwanted network access by apps, even if they have internet permission
- Prevent data leaks and unauthorized background connections
- Monitor network activity with comprehensive logging
- Save battery and data by controlling which apps can connect
- Enhance privacy by blocking tracking and analytics


### How It Works
AFWall+ operates at the Linux kernel level using `iptables` rules to:
1. Intercept all network requests before they leave your device
2. Apply custom firewall rules based on your preferences
3. Allow or block connections per app, per network type (WiFi, mobile, VPN)
4. Log blocked attempts for monitoring and analysis

This approach is much more powerful than app-level solutions because it works regardless of how apps try to connect to the internet.

---


## Download

<p align="left">
  <a href="https://play.google.com/store/apps/details?id=dev.ukanth.ufirewall">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">
  </a>
  <a href="https://f-droid.org/packages/dev.ukanth.ufirewall/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
  </a>
  <a href="https://github.com/ukanth/afwall/releases">
    <img src="https://img.shields.io/badge/GitHub-Releases-blue?style=for-the-badge&logo=github" alt="GitHub Releases" height="80">
  </a>
</p>


**Release Notes**: Check the [changelog](https://github.com/ukanth/afwall/blob/beta/Changelog.md) for what's new in each version.

---


## Key Features


### Granular Control
- Per-app network rules: Allow or block individual apps
- Network type filtering: Different rules for WiFi, mobile data, VPN, tethering
- IPv4 & IPv6 support
- Custom rule scripting for advanced users


### User Experience
- Clean, intuitive interface
- Quick search and filtering
- Bulk operations for multiple apps
- Profile management for different rule sets


### Monitoring & Logging
- Real-time network monitoring
- Detailed connection logs
- Notification system for blocked attempts
- Export/import rules for backup or sharing


### Advanced Features
- Boot protection: Apply rules before apps start
- Startup delay management
- Multi-user support
- Tasker/Locale integration for automation
- Password protection
- Tor and VPN detection


### Network Types Supported
- Mobile Data (3G/4G/5G), including roaming detection
- WiFi (home, work, public hotspots)
- VPN (all types and providers)
- Tethering (WiFi hotspot, USB, Bluetooth)
- Tor (onion routing support)
- LAN (local network access)

---


## System Requirements


### Compatibility
- Android versions: 5.0 (API 21) to 14+ (actively maintained)
  - Legacy support: Android 4.x (version 2.9.9), Android 2.x (version 1.3.4.1)
- Root access: Required (Magisk, SuperSU, LineageOS su)
- Architectures: ARM, ARM64, x86, x86_64
- Storage: ~15MB app + ~5MB for binaries


### Root Methods Supported
- Magisk (recommended)
- LineageOS built-in su
- SuperSU (legacy)
- KingRoot (not recommended)


### Limitations
- Requires root access (no root = no functionality)
- Not an antivirus (doesn't scan files for malware)
- Not an ad-blocker (blocks network access, not ads within allowed connections)
- VPN conflicts: Some VPN apps may interfere with firewall rules
- System-level apps: Some system processes may bypass rules if they have root access

---


## Quick Start Guide


### 1. Pre-Installation
```bash
# Verify root access
su -c "id"
# Should return: uid=0(root) gid=0(root)
```

### 2. Installation
- Install AFWall+ from your preferred source
- Grant root permission when prompted
- Enable the firewall on the main screen

### 3. Basic Configuration
1. Enable the firewall (toggle the main switch)
2. Configure apps (tap apps to allow WiFi or mobile data)
3. Apply rules (tap the apply button)
4. Test connectivity (verify apps work as expected)

### 4. Essential Settings
- Boot startup delay: Prevents rule conflicts during boot
- Notification settings: Control alert behavior
- Log settings: Enable if you want connection monitoring

---


## Advanced Configuration


### Custom Rules
AFWall+ supports custom iptables rules for advanced users:

```bash
# Example: Allow specific IP range
-A afwall-wifi -d 192.168.1.0/24 -j ACCEPT

# Example: Block specific port
-A afwall -p tcp --dport 443 -j REJECT
```


### Profiles
Create different rule sets for different scenarios:
- Home: Relaxed rules for trusted network
- Work: Restrictive rules for corporate network
- Public: Maximum security for public WiFi
- Travel: Balanced rules for mobile use


### Logging Configuration
- Packet logging: Uses nflog for detailed connection tracking
- Log rotation: Automatic cleanup of old logs
- Export options: Save logs for external analysis

---


## 🌍 Language Support

AFWall+ is available in **40+ languages** thanks to our community translators:

🇺🇸 English • 🇪🇸 Español • 🇫🇷 Français • 🇩🇪 Deutsch • 🇮🇹 Italiano • 🇷🇺 Русский • 🇨🇳 中文 • 🇯🇵 日本語 • 🇰🇷 한국어 • 🇵🇹 Português • 🇳🇱 Nederlands • 🇵🇱 Polski • 🇹🇷 Türkçe • 🇸🇦 العربية • 🇮🇳 हिंदी • And many more!

**Want to help translate?** Join our [Crowdin translation project](http://crowdin.net/project/afwall).

---


## Development


### Building from Source


#### Prerequisites
- Android SDK (API level 21+)
- Java 17+
- Git
- Android NDK (for native binaries)


#### Quick Build
```bash
git clone https://github.com/ukanth/afwall.git
cd afwall
./gradlew clean assembleDebug
```


#### Native Binaries
To compile iptables, busybox, and other native components:
```bash
# Requires Android NDK
export NDK=/opt/android-ndk-r25
make -C external NDK=$NDK
```


### Project Structure
```
afwall/
├── app/src/main/java/dev/ukanth/ufirewall/
│   ├── Api.java                    # Core iptables interface
│   ├── MainActivity.java           # Main UI
│   ├── InterfaceTracker.java       # Network state monitoring
│   ├── util/BootRuleManager.java   # Boot rule application
│   ├── service/                    # Background services
│   ├── broadcast/                  # System event receivers
│   └── log/                        # Logging subsystem
├── app/src/main/res/raw/           # Native binaries (iptables, busybox)
├── external/                       # Native binary sources
└── scripts/                        # Build scripts
```


### Testing
```bash
# Run lint checks
./gradlew lint

# Run unit tests
./gradlew test

# Install debug build
./gradlew installDebug
```

---


## Contributing

We welcome contributions! Here's how you can help:


### Bug Reports
- Check [existing issues](https://github.com/ukanth/afwall/issues) first
- Follow our [bug report guide](https://github.com/ukanth/afwall/wiki/HOWTO-Report-Bug)
- Include device info, Android version, and logs


### Feature Requests
- Open an issue with the "enhancement" label
- Describe the use case and expected behavior
- Consider if it fits AFWall+'s scope and philosophy


### Code Contributions
```bash
# Standard GitHub workflow
1. Fork the repository
2. Create a feature branch: git checkout -b feature-name
3. Make your changes and test thoroughly
4. Submit a pull request with a clear description
```


### Translations
- Join our [Crowdin project](http://crowdin.net/project/afwall)
- No technical knowledge required
- Help make AFWall+ accessible worldwide

---


## Community & Support

### Discussion Forums
- **XDA Thread**: [Official community discussion](http://forum.xda-developers.com/showthread.php?t=1957231)
- **GitHub Issues**: Technical problems and feature requests
- **Wiki**: [Comprehensive documentation](https://github.com/ukanth/afwall/wiki)

### Frequently Asked Questions
Before reporting issues, check our [FAQ](https://github.com/ukanth/afwall/wiki/FAQ) for common solutions.

### Getting Help
1. Check the FAQ and wiki
2. Search existing GitHub issues
3. Ask on XDA forums
4. Create a new GitHub issue (last resort)

---


## Technical Details


### Architecture
AFWall+ uses a layered architecture:

1. **UI Layer**: Android activities and fragments for user interaction
2. **Service Layer**: Background services for rule application and monitoring
3. **Core Layer**: iptables rule generation and management
4. **System Layer**: Native binaries and root shell interface


### Key Components
- BootRuleManager: Robust boot-time rule application with race condition prevention
- InterfaceTracker: Network interface monitoring and change detection
- Api.java: Central iptables command generation and execution
- FirewallService: Background service for continuous monitoring
- LogService: Network packet logging and analysis


### Android Integration
- Broadcast Receivers: Monitor system events (boot, network changes, app installs)
- Content Providers: Share configuration data securely
- Notification System: User alerts for blocked connections
- Quick Settings Tile: Fast firewall toggle (Android 7+)

---


## Acknowledgements

AFWall+ builds upon the work of many open-source projects and contributors:


### Origins
- Original concept: Derived from [DroidWall](http://code.google.com/p/droidwall) by Rodrigo Rosauro
- Current maintainer: [Umakanthan Chandran](https://github.com/ukanth)


### Libraries & Dependencies
| Component | License | Purpose |
|-----------|---------|---------|
| [iptables](http://netfilter.org/projects/iptables/) | GPL v2 | Linux firewall framework |
| [BusyBox](http://www.busybox.net) | GPL v2 | Unix utilities |
| [libsuperuser](https://github.com/Chainfire/libsuperuser) | Apache 2.0 | Root access management |
| [libsu](https://github.com/topjohnwu/libsu) | Apache 2.0 | Modern root interface |
| [Material Dialogs](https://github.com/afollestad/material-dialogs) | MIT | UI components |
| [DBFlow](https://github.com/Raizlabs/DBFlow) | MIT | Database ORM |
| [PrettyTime](https://github.com/ocpsoft/prettytime) | Apache 2.0 | Human-readable timestamps |


### Contributors
Thanks to all contributors who have helped improve AFWall+ over the years!

---


## License

AFWall+ is released under the **GNU General Public License v3.0**.

```
Copyright (C) 2009-2011 Rodrigo Zechin Rosauro
Copyright (C) 2011-2024 Umakanthan Chandran

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

**Full license text**: See the [LICENSE](LICENSE) file or [gnu.org/licenses/gpl-3.0](https://www.gnu.org/licenses/gpl-3.0.html)

---

<p align="center">
  <i>Made with ❤️ for Android privacy and security</i><br>
  <strong>AFWall+ - Your Network, Your Rules</strong>
</p>
