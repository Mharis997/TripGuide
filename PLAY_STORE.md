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

1. Support email is set to harism1407@gmail.com in `docs/privacy-policy.html` and `docs/community-rules.html` (also enter it as the Play Console contact email).
2. GitHub repo → Settings → Pages → Source: `main` branch, `/docs` folder.
3. Policy URL: `https://mharis997.github.io/tripguide/privacy-policy.html` (already linked from the app's Settings → Privacy policy; change `PRIVACY_URL` in `SettingsScreen.kt` if you host elsewhere).

## 4. Play Console setup

1. Create a developer account (one-time US$25) at play.google.com/console. Complete identity verification.
2. **Personal accounts created after Nov 2023 must run a closed test with at least 12 testers opted in for 14 continuous days before production access is granted.** Plan for this: upload the AAB to *Closed testing* first.
3. Create app → name "TripGuide Kerala: Trip Planner", default language English, App, Free.

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
- **Content rating questionnaire:** "All other app types" → no violence, sexual content, gambling or drugs in the app itself. **Users can share content with other users: Yes** (reels and places, with Report and auto-hide). Expected rating: Everyone / PEGI 3 with a "Users interact" notice.
- **Target audience:** 13–15, 16–17, 18+ (do not tick under-13 to stay out of the Families programme).
- **News app:** No.
- **Health / financial features / government app:** No.
- **Data safety:**
  - Does your app collect or share required user data types? **Yes**:
    - *Photos and videos → Photos*: **Collected**, shared with other users only when the user adds them to a place; optional; purpose **App functionality**.
    - *Photos and videos → Videos*: **Collected**, **processed ephemerally** (still frames only, for AI analysis, not stored), not shared, optional, **App functionality**.
    - *App activity → Other user-generated content* (reel links, place text, reports): **Collected**, optional, **App functionality**.
    - *Device or other IDs* (anonymous Firebase user ID): **Collected**, required for adding content, **App functionality**, **Account management**.
    - Location: **not collected** (used on-device only, unless the user saves it as a shared place's coordinates – disclose as *Approximate location*, optional, App functionality, if you want to be conservative).
  - Encrypted in transit? **Yes**. Can users request deletion? **Yes** – in-app delete for own content, and the email in the privacy policy.
- **User-generated content policy:** in-app Report, auto-hide at 3 reports, community rules link at every add screen (`docs/community-rules.html`). Check reported items in the Firebase console regularly.

## 5. Release
Testing → Closed testing → Create release → upload `app-release.aab` → release notes from `changelogs/1.txt` → review → roll out. After the 14-day / 12-tester requirement, apply for production and promote the release.

## Community values for release builds
Add `FIREBASE_PROJECT_ID`, `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `TRIPGUIDE_API_URL` as GitHub secrets (README → Community setup).
