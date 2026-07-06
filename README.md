# Caribù Android

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?logo=open-source-initiative&logoColor=white)](LICENSE)

Native Android companion app for [Caribù](https://github.com/travelermarco/caribu) — the unified camper van dashboard.

Brings Caribù to **Android Auto**: when your phone connects to your van's infotainment display, a live dashboard appears on the car screen showing battery SOC, heater temperature, and solar power — no manual steps needed.

## How it works

```
Chrome PWA (BLE active)
  └── Caribù PWA → every BMS update
        └── POST http://localhost:8888/state ──→ CaribuCarService
                                                       └── SharedPreferences
                                                             └── CaribuScreen (Car template)
                                                                   └── Android Auto display 🚗
```

1. **Phone**: the user keeps using the Caribù PWA from Chrome (BLE, all features unchanged)
2. **Bridge**: each BMS data update silently POSTs to `localhost:8888` — the native service picks it up
3. **Android Auto**: when the phone connects to the car, the system finds `CaribuCarService` and shows the Caribù dashboard on the car screen automatically

## Architecture

| File | Role |
|------|------|
| `MainActivity.kt` | Status screen + opens Chrome to the PWA |
| `CaribuCarService.kt` | Car App Service — registers with Android Auto |
| `CaribuScreen.kt` | Car template: PaneTemplate with live van data |
| `CaribuBridgeServer.kt` | HTTP server on `localhost:8888`, receives state from the PWA |
| `UpdateChecker.kt` | Auto-update via GitHub Releases (from marco-update-android) |

## Requirements

- Android 8.0+ (API 26)
- Android Auto installed and configured on the phone
- [Caribù PWA](https://github.com/travelermarco/caribu) open in Chrome when driving

## First-time setup

### 1. Enable Android Auto Developer Mode

Required to run sideloaded Car apps:

1. Phone → Settings → Connected devices → Android Auto
2. Tap the version number **10 times** to unlock Developer options
3. Developer options → **Unknown sources** → Enable

### 2. Install the APK

Download the latest APK from [Releases](https://github.com/travelermarco/caribu-android/releases) and install it on your phone.

### 3. First drive

1. Get in the van — phone connects to Android Auto automatically
2. Open the Caribù PWA in Chrome (same as you always do)
3. Connect your BLE devices (heater, BMS, MPPT)
4. The Caribù panel appears on the car display ✓

## Data shown on Android Auto

| Field | Source |
|-------|--------|
| 🔋 Battery SOC | XiaoXiang BMS via PWA |
| 🔥 Heater temperature | Vevor/Hcalory heater via PWA |
| ☀️ Solar power (W) | Victron SmartSolar MPPT via PWA |
| 🕐 Last update time | Bridge timestamp |

Data refreshes automatically whenever the PWA receives a BLE update (~every 30s).  
The car display shows "waiting for data" until Chrome is open with BLE connected.

## Build from source

**Requirements:** JDK 17, Android SDK (API 35), Gradle 8.6

```bash
# Set environment
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

# Build debug APK
gradle :app:assembleDebug

# Output
app/build/outputs/apk/debug/app-debug.apk
```

## Release workflow

Follow the standard [marco-update-android](https://github.com/travelermarco/marco-update-android) workflow:

1. Bump `VERSION_NAME` in `MainActivity.kt`
2. Build: `gradle :app:assembleDebug` (or `assembleRelease`)
3. Create a GitHub Release with tag `v{VERSION_NAME}`
4. Attach the APK as a release asset
5. Installed apps will prompt users to update on next launch

## Relationship with the PWA

This app does **not** replace the Caribù PWA — it extends it.

| | Caribù PWA | Caribù Android |
|-|-----------|----------------|
| Primary UI | ✅ Chrome (phone + Mac) | ❌ not the main UI |
| BLE control | ✅ Web Bluetooth in Chrome | ❌ |
| Android Auto display | ❌ | ✅ |
| Auto-update | ✅ Vercel (on push) | ✅ GitHub Releases |

## License

MIT
