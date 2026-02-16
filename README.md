# Scramblr's RF Toolkit

Android-native WiFi, Bluetooth, and BLE scanner with WiGLE-compatible database logging and Signal Finder tracking mode.

**Android Only** - No iOS support (iOS doesn't allow the low-level WiFi/BLE access needed for wardriving).

## Features

- **WiFi Scanning** - Detect networks with SSID, BSSID, channel, security type, signal strength
- **BLE Scanning** - Bluetooth Low Energy device detection with manufacturer data
- **BLE RPA Resolution** - Resolve Resolvable Private Addresses using Identity Resolving Keys (IRKs)
- **Signal Finder** - Track specific devices by MAC address or IRK with haptic feedback
- **GPS Logging** - Record coordinates for every network observation
- **WiGLE Export** - Export to CSV format compatible with WiGLE.net uploads
- **SQLite Database** - Local storage with WiGLE-compatible schema

## Prerequisites

### Required Software

1. **Android Studio** (Hedgehog 2023.1.1 or newer)
   - Download: https://developer.android.com/studio
   
2. **JDK 17** (usually bundled with Android Studio)

3. **Android SDK**
   - API Level 34 (Android 14)
   - Build Tools 34.0.0
   - Android Studio will prompt to install these

### Hardware

- Android device running **Android 8.0 (API 26)** or higher
- Device with WiFi and Bluetooth capabilities
- GPS enabled

## Build Instructions

### Step 1: Clone or Extract

```bash
# If using git
git clone https://github.com/scramblr/rf-toolkit.git
cd rf-toolkit

# Or extract the zip file to a folder
```

### Step 2: Open in Android Studio

1. Launch Android Studio
2. Select **File → Open**
3. Navigate to the `rf-toolkit-android` folder
4. Click **OK**
5. Wait for Gradle sync to complete (may take several minutes on first run)

### Step 3: Configure Google Maps (Optional)

If you want the Map feature to work:

1. Get a Google Maps API key from https://console.cloud.google.com/
2. Enable "Maps SDK for Android"
3. Edit `app/src/main/AndroidManifest.xml`
4. Replace `YOUR_GOOGLE_MAPS_API_KEY` with your actual key

### Step 4: Build APK

**Option A: Debug APK (for testing)**
```
Menu: Build → Build Bundle(s) / APK(s) → Build APK(s)
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Option B: Signed Release APK**
1. Menu: Build → Generate Signed Bundle / APK
2. Select APK
3. Create or use existing keystore
4. Select "release" build variant
5. Click Finish

Output: `app/build/outputs/apk/release/app-release.apk`

### Step 5: Install on Device

**Via USB:**
1. Enable Developer Options on your Android device
2. Enable USB Debugging
3. Connect device via USB
4. In Android Studio, click the green Run button
5. Select your device

**Via APK file:**
1. Copy the APK to your device
2. Enable "Install from unknown sources" in settings
3. Open the APK file to install

## Usage

### Dashboard
- Tap **START SCAN** to begin scanning
- Networks appear in real-time as they're detected
- Stats show WiFi, Bluetooth, new networks, and totals
- GPS coordinates update as you move

### Signal Finder
1. Enter a MAC address (e.g., `AA:BB:CC:DD:EE:FF`) or select an IRK
2. Tap **START SEARCH**
3. Move around - device vibrates when target is detected
4. Signal strength and distance estimate update in real-time

### Database
- View statistics on discovered networks
- **Export CSV** - Creates WiGLE-compatible file in Downloads folder
- **Clear All Data** - Permanently delete all stored data

### Settings
- Enable/disable WiFi, BLE, Classic Bluetooth scanning
- Set minimum signal strength threshold
- Enable/disable GPS route logging
- Configure vibration/sound feedback

## Permissions

The app requires these permissions:

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
The CSV export uses WiGLE's format:
```
MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type
```

### Uploading to WiGLE
1. Go to Database tab
2. Tap "Export to CSV"
3. Share/save the file
4. Upload at https://wigle.net/uploads

## BLE RPA Resolution

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

## Credits

- **btrpa-scan** by David Kennedy (@HackingDave) - BLE RPA resolution algorithm
- **WiGLE WiFi Wardriving** - Database schema and export format inspiration

## License

MIT License

## Disclaimer

This tool is for educational and authorized security research only. Always obtain permission before scanning networks you don't own. Users are responsible for complying with all applicable laws.
