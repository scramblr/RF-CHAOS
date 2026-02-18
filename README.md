# RF CHAOS - Chaos via BLE / BT / WiFi / NFC / Etc via Android Phones
### **RF CHAOS is an Android-Native WiFi, Bluetooth, BLE, and NFC scanner, logger, and explotation tool.**
##### _Created By [Scramblr](https://github.com/scramblr) AKA [@notdan](https://x.com/notdan) AKA alotofnamesyoudontknow_

### **ℹ️ If you're itching to get the APK now, compiled releases are here: [RF-CHAOS .APK INSTALLERS](https://github.com/scramblr/RF-CHAOS/releases/) ℹ️**

# INTRODUCTION & PREFACE
RF CHAOS is pretty heavily based on a bunch of tools that paved the way for Maximum Mobile Hacking over the years, including:
- [WIGLE.NET](https://wigle.net)
- [Sniffle](https://github.com/nccgroup/Sniffle)
- [btrpa-scan](https://github.com/hackingdave/btrpa-scan)
- [bsniffhub](https://github.com/homewsn/bsniffhub)
- [bluez](https://github.com/bluez/bluez)

**..And many, many more tools and projects - too many to list.**

I've been wanting to build a toolkit like RF-CHAOS for years now but just never got the time or opportunity. So, since I saw some motivation via X I figured might as well do it up. Since there's already a bunch of tools that exist for this on computer platforms, I figured I'd focus my effort on mobile devices, and since Android is the only one that allows low level access to WiFi/BLE/Bluetooth/etc, they were the winner!

I hope you have some fun with it. Evil fun. 😈🤘

# WELL OF COURSE THERE'S SECURITY CONCERNS
It turns out, for BLE especially, there's been some developments over the years that make targeting some devices more difficult than it used to be! I'm talking about those "Private MAC Addresses" that change every 15 minutes or so.
### But don't worry, this tool will give you the ability to track and hunt equipment that is using this _SUPER SECURE PRIVACY FEATURE (lol)_ AKA Device Privacy via Resolvable Private Addresses (RPAs)!

## THE "PRIVACY FEATURE" WHICH SPOOFS BLUETOOTH MAC ADDRESSES IS BULLSHIT.
🚨👮⚠️ What is BLE/Bluetooth RPA Resolution/Tracking? It's the ability to track all of those phones and devices that made you think you might have regained some of your privacy back through that feature you've likely seen by now, the one that supposedly changes your device's MAC address to a spoofed private address. Your phone or device's MAC Addres changes to a new spoofed address every 15 minutes or so.

And it's all just a fucking lie.

#### Sadly, but predictably, this whole idea is basically bullshit and reeks of industry collusion with Law Enforcement Agencies to decieve consumers into having a false sense of security while they (as well as other determined adversaries) are able to track each new "Private MAC Address" with just one piece of information called an IRK (Identity Resolving Key). It takes knowing your device's IRK code - obtainable through a variety of methods - to decode and track every single new spoofed MAC address, making your device(s) every single movement, usually within a resolution of centimeters. 

This, all while your device's menus list this as a "Privacy Feature" and proclaim to be protecting you against this very type of attack. Are you less than excited about being tracked by anyone, including Law Enforcement, at any time? Want to see how it works first hand? You're in the right place! Yes, there's details and more to this, so keep reading. 🚨👮⚠️

 **TL;DR: Every single moment your Bluetooth Adapter is powered you are 100% trackable, even with Private MAC Spoofing turned on.**

---
# RF-CHAOS: Primary Features & Functions

- **WiFi Scanning** - Detect networks with SSID, BSSID, channel, security type, signal strength
- **BLE Scanning** - Bluetooth Low Energy device detection with manufacturer data
- **BLE RPA Resolution** - Resolve (and then track devices) via RPAs (Resolvable Private Addresses) by using IRK (Identity Resolving Key) values. _Basically, IRKs allow you to have the magic decoder ring for finding your target even when they're using a fake MAC address._
- **Signal Finder** - Track specific devices by MAC address or IRK with haptic feedback - Works for WiFi as well as BLE/Bluetooth.
- **GPS Logging** - Record coordinates for every network observation as well as route travelled, LOCALLY on your phone.
- **WiGLE Export** - Export your local database to CSV format, specifically compatible with WiGLE.net's API/Systems in case you want to share with their platform!
- **SQLite Database** - Local-Only Storage on Android Phone Only for Paranoid Types. Uses WiGLE-compatible schema for easy sharing in case you get frisky.

# RF-CHAOS: Required Stuff Needed to Build From Source

### Required Libraries & Software

1. **Android Studio** (Hedgehog 2023.1.1 or newer)
   - Download: https://developer.android.com/studio
   
2. **JDK 17** (usually with Android Studio)

3. **Android SDK**
   - API Level 34 (Android 14)
   - Build Tools 34.0.0
   - Android Studio will prompt to install these right after importing the project

### Hardware & Devices That Work

- Android device running **Android 8.0 (API 26)** or higher. (It works with almost any phone made in the last 1,000 years)
- Android must have WiFi & Bluetooth radios that aren't broken. Do I really need to write this? Probably.
- GPS or GPS Spoofer Enabled. This is for the Mapping function and ZERO telemetry is ever sent to our servers or any servers (look at the source code)

# Building Your Own RF-CHAOS APK via Source Code

### Step 1: Clone or Extract from Github Repo

```bash
# If using git
git clone https://github.com/scramblr/RF-CHAOS.git
cd RF-CHAOS

# Or extract the zip file to a folder
```

### Step 2: Open in Android Studio

1. Launch Android Studio
2. Select **File → Open**
3. Navigate to the `RF-CHAOS` folder
4. Click **OK**
5. Wait for Gradle sync to complete (may take several minutes on first run)

### Step 3: Configure No Google Maps API Keys Because We Use OpenStreetMaps (free + unlimited) instead!
In a release coming in the next few weeks you'll have the option to switch to Google Maps SDK. I even staged the Manifest Instructions:

```
0. Wait for feature to actually be built and tested.
1. Get a Google Maps API key from https://console.cloud.google.com/
2. Enable "Maps SDK for Android"
3. Edit `app/src/main/AndroidManifest.xml`
4. Replace `YOUR_GOOGLE_MAPS_API_KEY` with your actual key.
```

### Step 4: Build Debug or Production Release APKs

**Testing & Debug .APK or Bundle .AAB Packages**
###### *NOTE: Menu wording may be slightly different depending on Android Studio version installed.
```
Menu: Build → Generate and Build App Bundles or APKs → Generate APKs / Generate Bundles
```

```
APK Output: `app/build/outputs/apk/debug/app-debug.apk`
Bundle Output: `app/build/outputs/bundle/debug/app-debug.aab`
```

**Production Release .APK or Bundle .AAB Packages**
1. Menu: Build → Generate Signed Bundle / APK
2. Select APK (Or Bundle if needed)
3. Create or use existing keystore using prompts (Optional)
4. Select "release" build variant

```
APK Output: `app/release/app-release.apk`
Bundle Output: `app/release/app-release.aab`
```

### Step 5: Installation on Your Device

- **Via USB ADB Bridge:**
1. Enable Developer Options on your Android device (Tap your Android Build Version number 69 times)
2. Enable USB Debugging
3. Connect device via USB
4. In Android Studio, make sure your phone is listed in the upper right window text.
5. Click the Green Run Button (Looks like Play Button)

Your phone should have RF-CHAOS installed on it within a few seconds! It'll request the 3 primary security permissions needed to search for signals and plot coordinates with GPS.

- **Via APK file:**
1. Copy the APK to your device (adb push filename.apk /sdcard/Download or wherever/)
2. Enable "Install from unknown sources" in settings
3. Open the APK file & install

# RF-CHAOS USAGE

### Dashboard
- Tap **START SCAN** to begin scanning
- Networks appear in real-time as they're detected
- Stats show things like the number of WiFi, Bluetooth, New Networks, Beacon Counts, and more.
- Realtime GPS Coordinates (Latitude & Longitude) as you move, walk, drive, etc. 

### Signal Finder
1. Select Bluetooth/BLE/WiFi/IRK
2. Enter a MAC address (Format should be `AA:BB:CC:DD:EE:FF`) or IRK code.
3. Tap **START SEARCH**
4. Move around - device vibrates when target is detected
5. Signal strength and distance estimate update in real-time and changes from Red to Green when getting closer.

### Database
- View statistics on discovered networks
- **Export CSV** - Creates WiGLE-compatible file in Downloads folder
- **Clear All Data** - Permanently delete all stored data

### Settings
- Enable/disable WiFi, BLE, Classic Bluetooth scanning (Be sure to stop scanning before changing this. Might need app restart)
- Set minimum signal strength threshold if needed.
- Enable/disable GPS route logging (Optional)
- Configure vibration/sound feedback (Optional)

## Permissions Needed to run RF-CHAOS

The app requires permissions to access the radios inside your phone or device. The App should NEVER phone home. All data stays on your device unless you EXPLICITLY want to share it with WIGLE.NET.

| Permission | Reason |
|------------|--------|
| ACCESS_FINE_LOCATION | Required for WiFi/BLE scanning and GPS |
| ACCESS_COARSE_LOCATION | Location fallback |
| BLUETOOTH_SCAN | BLE device scanning (Android 12+) |
| BLUETOOTH_CONNECT | Bluetooth connections (Android 12+) |
| NEARBY_WIFI_DEVICES | WiFi scanning (Android 13+) |
| VIBRATE | Signal Finder haptic feedback |

## WiGLE Integration

### Export Format
The CSV export uses WiGLE's format. We save it inside of an SQLite Database locally on your device with the following tables:
```
MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type
```
### Uploading to WiGLE
1. Go to Database tab
2. Tap "Export to CSV"
3. Share/save the file
4. Upload at https://wigle.net/uploads

---

# PRIVACY NIGHTMARE MODE
## BLE RPA Resolution & Tracking Devices Attempting to Hide with IRK Codes

Bluetooth Low Energy devices can use Resolvable Private Addresses (RPAs) that change periodically. If you have a device's Identity Resolving Key (IRK), you can track it despite address changes.

### How to use:
1. In Signal Finder, tap "Mode: MAC Address" to switch to "Mode: IRK"
2. Add your IRK (32 hex characters)
3. Start search - the app will resolve RPAs in real-time

### Getting IRKs
IRKs can be extracted from:
- Paired device records on Android/iOS
- BLE pairing packet captures
- Device manufacturer documentation

## Project Structure

```
app/src/main/
├── java/com/scramblr/rftoolkit/
│   ├── MainActivity.kt          # Main activity with navigation
│   ├── RFToolkitApp.kt          # Application class
│   ├── data/
│   │   ├── db/AppDatabase.kt    # Room database & DAOs
│   │   ├── models/Models.kt     # Data classes
│   │   └── repository/          # Data access layer
│   ├── services/
│   │   └── ScanningService.kt   # Foreground scanning service
│   ├── ui/                      # Fragments for each screen
│   └── utils/
│       └── Scanners.kt          # WiFi/BLE scanning + RPA resolver
├── res/
│   ├── layout/                  # XML layouts
│   ├── navigation/              # Navigation graph
│   └── values/                  # Colors, strings, themes
└── AndroidManifest.xml
```

## Troubleshooting

### "Location permission denied"
- Go to device Settings → Apps → RF Toolkit → Permissions
- Grant Location permission (set to "Allow all the time" for background scanning)

### WiFi networks not appearing
- Ensure WiFi is enabled on device
- On Android 13+, grant "Nearby devices" permission
- Some devices throttle WiFi scans - wait a few seconds between scans

### BLE devices not appearing
- Ensure Bluetooth is enabled
- Grant Bluetooth permissions when prompted
- BLE scanning requires Location permission

### Build fails with "SDK not found"
- In Android Studio: File → Project Structure → SDK Location
- Ensure Android SDK path is correct
- Run: Tools → SDK Manager to install missing components

### Gradle sync fails
- File → Invalidate Caches and Restart
- Delete `.gradle` folder in project root
- Re-sync project

## ©2026 SCRAMBLR AKA NOTDAN AKA IDUNNO

## License

GNU General Public License

## Disclaimer

Don't die!
