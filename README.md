# 🛣️ PothBondhu (পথবন্ধু)

> **A Friend on the Road** — A comprehensive emergency assistance Android application designed to improve road safety for daily commuters, students, and travelers in Bangladesh.

<div align="center">

![Platform](https://img.shields.io/badge/Platform-Android-green?logo=android)
![Language](https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin)
![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-blue)
![Target SDK](https://img.shields.io/badge/Target%20SDK-35%20(Android%2015)-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)
![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen)

</div>

---

## 📖 Table of Contents

- [About](#-about)
- [Features](#-features)
- [Tech Stack](#️-tech-stack)
- [Project Structure](#-project-structure)
- [Architecture](#️-architecture)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Firebase Setup](#firebase-setup)
  - [Installation](#installation)
- [Usage](#-usage)
- [API Integrations](#-api-integrations)
- [Permissions](#-permissions)
- [Dependencies](#-dependencies)
- [Testing](#-testing)
- [Roadmap](#️-roadmap)
- [Contributing](#-contributing)
- [License](#-license)
- [Acknowledgements](#-acknowledgements)

---

## 📱 About

**PothBondhu** (পথবন্ধু — meaning *"Road Friend"* in Bengali) is a **native Android emergency assistance application** built with Kotlin that consolidates critical road safety features into a single, easy-to-use platform. Designed specifically for daily commuters, students, and travelers in Bangladesh, the app works reliably even in low-connectivity environments.

From a one-tap SOS alert that sends your live GPS location via SMS, to accelerometer-based crash detection, a nearby hospital and police finder, and a medical profile for first responders — PothBondhu delivers a functional, production-quality safety tool with the potential to save lives on the road.

- **Package:** `com.jgd.pothbondhu.myapplication.app`
- **Min SDK:** 24 (Android 7.0 Nougat)
- **Target SDK:** 35 (Android 15)
- **Tested On:** Android 12 (API 31) — Physical Device
- **Language:** Kotlin
- **Version:** 1.0

---

## ✨ Features

### 🆘 One-Tap SOS Alert System
- **Instant SOS trigger** with a **5-second cancellable countdown** to prevent accidental activation
- Sends the user's **live GPS coordinates** and **medical profile** to pre-configured emergency contacts
- Delivered via **SMS** — works **without an internet connection**
- Logs every SOS event to Firebase Firestore for history and accountability

### 💥 Accelerometer-Based Crash Detection
- Continuously monitors device motion using Android's native **accelerometer sensor**
- Detects sudden impact patterns consistent with a vehicle collision
- **Adjustable sensitivity** to reduce false positives
- Automatically triggers the SOS flow upon confirmed crash detection
- Runs as a reliable **background service**

### 🗺️ Nearby Help Finder
- **Interactive map** powered by **OSMDroid (OpenStreetMap)** — no Google Maps API key required
- One-tap category filters for:
  - 🏥 Hospitals
  - 🚓 Police Stations
  - ⛽ Fuel Stations
  - 🔧 Mechanics
  - 🩸 Blood Banks
- **POI data** sourced from the **Nominatim / Overpass API**
- **Place details bottom sheet** — shows name, address, and distance
- **Get Directions** — opens **Google Maps** navigation for the selected place
- **Live location tracking** with a "My Location" FAB button

### 🧭 Google Maps Navigation Integration
- Seamlessly hands off navigation to **Google Maps** for turn-by-turn directions to any nearby POI or destination

### 👤 Medical Profile for First Responders
- Store critical health information locally and in the cloud:
  - Blood group, allergies, chronic conditions, current medications
  - Emergency contact name and phone number
- Profile is automatically **attached to every SOS alert** sent via SMS
- Stored securely in **Firebase Firestore**

### ⏱️ Journey Timer with Missed Check-In Alerts
- Set a **journey duration** before departure
- App monitors progress and triggers an **automatic alert** if the user fails to check in by the estimated arrival time
- Notifies emergency contacts on a missed check-in
- Journey history is stored in Firebase Firestore

### 🩸 Blood Bank Finder
- Locate nearby blood banks and donation centers using OpenStreetMap data
- Integrated directly into the Nearby Help Finder map view

### 🚗 Uber Deep-Link Ride Booking
- **One-tap ride booking** via Uber's deep link API
- Sets the selected POI or destination as the drop-off point automatically

### 🔐 Authentication
- **Email & Password Login** — Secure login via Firebase Authentication
- **User Registration** — Sign up with name, email, phone, and password
- **Password Reset** — Email-based recovery
- **Session Persistence** — Auto-login for returning users
- **Splash Screen** — Smart redirect based on authentication state

### ☁️ Cloud Sync & Storage
- All user data (profile, emergency contacts, SOS logs, journey history) stored in **Firebase Firestore**
- Real-time sync across sessions and devices

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | XML Layouts, Material Design 3 |
| Architecture | Activity-based (MVC pattern) |
| Authentication | Firebase Authentication (Email/Password) |
| Database | Firebase Firestore |
| Maps | OSMDroid (OpenStreetMap) |
| POI Search | Nominatim API / Overpass API |
| Navigation | Google Maps (deep link) |
| Ride Booking | Uber (deep link) |
| Networking | Retrofit2 + OkHttp3 + Gson |
| Async | Kotlin Coroutines |
| Background Work | Android Background Services |
| Sensors | Android Accelerometer API (crash detection) |
| SMS | Android native SmsManager |
| Storage | SharedPreferences (local) + Firebase Firestore (cloud) |
| Location | Google Play Services — FusedLocationProvider |
| Build | Gradle (Kotlin DSL) |

---

## 📁 Project Structure

```
PothhBondhu/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/jgd/pothbondhu/myapplication/app/
│   │   │   │   ├── SplashScreenActivity.kt         # Splash + auth routing
│   │   │   │   ├── LoginActivity.kt                # Email/password login
│   │   │   │   ├── SignupActivity.kt               # User registration
│   │   │   │   ├── AuthRepository.kt               # Firebase auth logic layer
│   │   │   │   ├── MainActivity.kt                 # Home dashboard
│   │   │   │   ├── NearbyActivity.kt               # Map + nearby POI search
│   │   │   │   ├── SosActivity.kt                  # SOS trigger + countdown
│   │   │   │   ├── CrashDetectionService.kt        # Accelerometer crash detector
│   │   │   │   ├── JourneyTimerActivity.kt         # Journey timer + check-in
│   │   │   │   ├── MedicalProfileActivity.kt       # First-responder health info
│   │   │   │   ├── BloodBankActivity.kt            # Blood bank finder
│   │   │   │   ├── EmergencyContactsActivity.kt    # Manage SOS contacts
│   │   │   │   ├── SosLogActivity.kt               # SOS history log
│   │   │   │   ├── User.kt                         # User data class
│   │   │   │   └── ...                             # Additional data classes
│   │   │   ├── res/                                # Layouts, drawables, values
│   │   │   └── AndroidManifest.xml
│   ├── build.gradle                                # App-level dependencies
│   └── google-services.json                        # Firebase config (not in VCS)
├── build.gradle.kts                                # Project-level build config
├── settings.gradle.kts
└── gradle.properties
```

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                            UI Layer                              │
│  SplashScreen → Login / Signup                                   │
│         ↓                                                        │
│     MainActivity (Home Dashboard)                                │
│       ├── SosActivity          (SOS + 5s countdown)             │
│       ├── CrashDetectionService (Background accelerometer)       │
│       ├── NearbyActivity       (OSMDroid map + POI search)       │
│       ├── MedicalProfileActivity (First-responder profile)       │
│       ├── EmergencyContactsActivity (SOS contact management)     │
│       ├── JourneyTimerActivity (Timer + missed check-in)         │
│       ├── BloodBankActivity    (Blood bank finder)               │
│       └── SosLogActivity       (SOS history)                    │
└────────────────────────┬─────────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────────┐
│                      Data / Logic Layer                          │
│  AuthRepository  ──────────────►  Firebase Auth                  │
│  Firestore (profiles, logs) ───►  Firebase Firestore             │
│  SmsManager ───────────────────►  Android SMS (offline capable)  │
│  SensorManager (Accelerometer) ►  Crash Detection                │
│  FusedLocationProviderClient ──►  Device GPS                     │
│  OSMDroid MapView ─────────────►  OpenStreetMap Tiles            │
│  Nominatim / Overpass API ─────►  POI & Place Data               │
│  SharedPreferences ────────────►  Local profile cache            │
└──────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17**
- **Android device or emulator** running API 24+ (physical device recommended for sensor testing)
- **Google account** for Firebase setup
- Active **internet connection** for cloud features (SOS SMS works offline)

### Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/) and create a new project.
2. Add an Android app with package name: `com.jgd.pothbondhu.myapplication.app`
3. Download `google-services.json` and place it in the `app/` directory.
4. Enable the following Firebase services:
   - **Authentication** → Email/Password
   - **Firestore Database** → Create in test mode initially
5. Configure Firestore security rules:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
       match /sos_logs/{logId} {
         allow read, write: if request.auth != null;
       }
       match /journeys/{journeyId} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/PothBondhu.git
   cd PothBondhu
   ```

2. **Open in Android Studio:**
   - File → Open → Select the `PothhBondhu` directory

3. **Add Firebase config:**
   - Place your `google-services.json` in `app/google-services.json`

4. **Sync Gradle:**
   - Click **Sync Now** in the notification bar, or go to File → Sync Project with Gradle Files

5. **Run the app:**
   - Connect a physical Android device (recommended) and click ▶ **Run**

> **Note:** Features such as crash detection and GPS location require a physical device for full testing.

---

## 📲 Usage

### First Launch
1. App opens with the **Splash Screen**
2. If not logged in → **Login Screen**
3. New users can tap **Sign Up** to register

### Sending an SOS Alert
1. Tap the **SOS** button from the home screen
2. A **5-second countdown** begins — tap **Cancel** to abort if triggered accidentally
3. After countdown, the app sends your **live GPS location + medical profile** to all emergency contacts via **SMS**
4. The event is logged to Firebase Firestore

### Crash Detection
1. Enable **Crash Detection** from the home screen or settings
2. The app runs a **background service** monitoring the accelerometer
3. Adjust sensitivity as needed
4. On detecting a crash-like impact, the SOS flow is automatically triggered

### Finding Nearby Help
1. Open **Nearby** from the home screen
2. Grant **Location Permission** when prompted
3. Tap a category: Hospitals, Police, Fuel, Mechanics, or Blood Banks
4. Tap a map marker to view place details
5. Use **Get Directions** to navigate via Google Maps
6. Use **Book Ride** to launch Uber with the destination pre-filled

### Setting Up a Journey Timer
1. Open **Journey Timer**
2. Enter your estimated journey duration
3. Tap **Start Journey**
4. Check in when you arrive — if you miss the check-in, an alert is automatically sent to your emergency contacts

### Managing Your Medical Profile
1. Open **Medical Profile** from the home screen
2. Fill in: blood group, allergies, conditions, medications, and emergency contact
3. This data is automatically included in every SOS message sent

---

## 🌐 API Integrations

### Nominatim / Overpass API (OpenStreetMap)
Used for querying nearby points of interest using open OSM data.

- **Overpass Endpoint:** `https://overpass-api.de/api/interpreter`
- **Method:** `POST` with OverpassQL query
- **Supported amenity tags:** `hospital`, `police`, `fuel`, `mechanic`, `blood_bank`

### Google Maps Navigation
```
https://www.google.com/maps/dir/{startLat},{startLon}/{destLat},{destLon}
```

### Uber Ride Booking Deep Link

| Service | Deep Link Format |
|---|---|
| Uber | `uber://?action=setPickup&pickup=my_location&dropoff[latitude]=...&dropoff[longitude]=...` |

---

## 🔒 Permissions

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS-based precise location for map, SOS alerts & search |
| `ACCESS_COARSE_LOCATION` | Fallback approximate location |
| `INTERNET` | API calls, Firebase, map tile loading |
| `ACCESS_NETWORK_STATE` | Check connectivity before network requests |
| `SEND_SMS` | Sending SOS alert messages without internet |
| `RECEIVE_BOOT_COMPLETED` | Restart background crash detection service after reboot |
| `FOREGROUND_SERVICE` | Keep crash detection running in the background |

---

## 📦 Dependencies

```groovy
// AndroidX Core
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.cardview:cardview:1.0.0'

// Firebase
implementation platform('com.google.firebase:firebase-bom:33.1.0')
implementation 'com.google.firebase:firebase-auth-ktx'
implementation 'com.google.firebase:firebase-firestore-ktx'

// OpenStreetMap
implementation 'org.osmdroid:osmdroid-android:6.1.18'

// Networking
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

// Location
implementation 'com.google.android.gms:play-services-location:21.0.1'

// Coroutines
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.8.7'
```

---

## 🧪 Testing

All core features have been **manually tested on a physical Android device** running **Android 12 (API 31)**:

| Feature | Status |
|---|---|
| SOS alert (SMS delivery) | ✅ Tested |
| 5-second cancellable countdown | ✅ Tested |
| GPS location in SOS SMS | ✅ Tested |
| Crash detection (accelerometer) | ✅ Tested |
| Nearby hospital / police / fuel search | ✅ Tested |
| Google Maps navigation handoff | ✅ Tested |
| Uber deep-link ride booking | ✅ Tested |
| Medical profile save & retrieve | ✅ Tested |
| Journey timer & missed check-in alert | ✅ Tested |
| Blood bank finder | ✅ Tested |
| Firebase Auth (login / signup) | ✅ Tested |
| Firebase Firestore sync | ✅ Tested |

---

## 🗺️ Roadmap

Planned features for future releases:

- [ ] **Bangla Language Support** — Full Bengali UI localization
- [ ] **Push Notifications (FCM)** — Real-time alerts for missed check-ins and SOS confirmations
- [ ] **Community Hazard Reporting** — Crowdsourced road hazard pins on the map
- [ ] **Pathao Integration** — Local ride-sharing deep link support
- [ ] **Google Sign-In** — One-tap authentication via Android Credential Manager
- [ ] **Offline Maps** — Cached map tiles for no-connectivity zones
- [ ] **Widget / Quick-Access Tile** — SOS trigger from the notification shade

---

## 🤝 Contributing

Contributions are welcome! To contribute:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add your feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

Please ensure your code follows the existing Kotlin style guide and includes comments for complex logic.

---

## 📄 License

This project is licensed under the **MIT License**.

```
MIT License

Copyright (c) 2026 PothBondhu Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 🙏 Acknowledgements

- [OpenStreetMap](https://www.openstreetmap.org/) — Free, open map data and POI search via Overpass API
- [OSMDroid](https://github.com/osmdroid/osmdroid) — Android OpenStreetMap library
- [Nominatim](https://nominatim.org/) — OSM-based geocoding and place search
- [Firebase](https://firebase.google.com/) — Authentication and real-time cloud database
- [Google Play Services](https://developers.google.com/android/guides/overview) — Fused location provider
- [Uber Deep Links](https://developer.uber.com/docs/riders/ride-requests/tutorials/deep-links/introduction) — Ride booking integration
- [Material Design 3](https://m3.material.io/) — Modern Android UI components and guidelines

---

<div align="center">

Made with ❤️ for the safety of road users in Bangladesh 🇧🇩

*পথে থাকুন, নিরাপদে থাকুন — Stay on the road, stay safe.*

</div>
