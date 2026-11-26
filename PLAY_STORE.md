# Publishing TripGuide on Google Play

Package name: `com.tripguide.app` (permanent once uploaded). Target SDK 36, min SDK 26 (Android 8.0+).

## 1. Create your upload key (once, keep it safe forever)

```bash
keytool -genkeypair -v -keystore tripguide-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Create `keystore.properties` in the project root (git-ignored):

```properties
storeFile=tripguide-upload.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=upload
keyPassword=YOUR_KEY_PASSWORD
```

Back up the `.jks` and passwords outside the repo. With Play App Signing (default), Google holds the real app-signing key; a lost upload key can be reset via Play support, but it takes days.

## 2. Build the release bundle

```bash
./gradlew testDebugUnitTest bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

For every update: bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, and add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## 3. Host the privacy policy

1. Replace `SUPPORT_EMAIL` in `docs/privacy-policy.html` with your support address.
2. GitHub repo → Settings → Pages → Source: `main` branch, `/docs` folder.
3. Policy URL: `https://mharis997.github.io/tripguide/privacy-policy.html` (already linked from the app's Settings → Privacy policy; change `PRIVACY_URL` in `SettingsScreen.kt` if you host elsewhere).

## 4. Play Console setup

1. Create a developer account (one-time US$25) at play.google.com/console. Complete identity verification.
2. **Personal accounts created after Nov 2023 must run a closed test with at least 12 testers opted in for 14 continuous days before production access is granted.** Plan for this: upload the AAB to *Closed testing* first.
3. Create app → name "TripGuide: Trip Planner", default language English, App, Free.

### Store listing (text is ready in `fastlane/metadata/android/en-US/`)
| Field | File / asset |
|---|---|
| App name (≤30) | `title.txt` |
| Short description (≤80) | `short_description.txt` |
| Full description (≤4000) | `full_description.txt` |
| App icon 512×512 PNG | `images/icon.png` |
| Feature graphic 1024×500 | `images/featureGraphic.png` |
| Phone screenshots (2–8) | `images/phoneScreenshots/*.png` (regenerate: `./gradlew recordRoborazziDebug`) |

Category: **Travel & Local**. Tags: Trip planner, Travel guide.

### App content answers
- **Privacy policy:** URL from step 3.
- **Ads:** No ads.
- **App access:** All functionality available without special access.
- **Content rating questionnaire:** Category "Reference, News, or Educational"/"All other app types" → no violence, sexual content, gambling, drugs. Users do **not** interact or share content with other users (updates stay on-device in this version). Expected rating: Everyone / PEGI 3.
- **Target audience:** 13–15, 16–17, 18+ (do not tick under-13 to stay out of the Families programme).
- **News app:** No.
- **Health / financial features / government app:** No.
- **Data safety:**
  - Does your app collect or share any of the required user data types? **Yes** (only because Discover search text leaves the device):
    - *App activity → In-app search history*: **Collected**, not shared, **processed ephemerally**, not required (user can skip Discover), purpose **App functionality**.
    - Nothing else: location is read only on request and never transmitted; Google/Open-Meteo receive destination coordinates, not user data.
  - Is data encrypted in transit? **Yes** (HTTPS only).
  - Can users request data deletion? **Yes** — Settings → Delete all my data / uninstall.

## 5. Release
Testing → Closed testing → Create release → upload `app-release.aab` → release notes from `changelogs/1.txt` → review → roll out. After the 14-day / 12-tester requirement, apply for production and promote the release.

## Google Maps key for release builds
Follow README → "Google Maps setup". Before uploading, add the **Play App Signing** SHA-1 (Play Console → App integrity) to the key's Android restrictions, otherwise Google features fail in the Play-installed app while working in your own builds.

Google's terms require the "Google Maps" attribution under Places results (already shown) and forbid storing Places content – the app only displays it live.

## Before adding online features
Sharing updates between users, cloud sync or push alerts to *other* travellers needs a backend (e.g. Firebase Firestore + Cloud Messaging). When you add it, update the Data safety form (user-generated content, app activity), the content rating (users interact), the privacy policy, and add a report/block mechanism for user posts (Play's UGC policy requires moderation).
