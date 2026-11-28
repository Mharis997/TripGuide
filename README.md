# TripGuide Kerala – Reels Trip Directory (Android)

A Kerala travel directory you swipe like reels. Every place shows the Instagram reels travellers shared about it, and the tips, prices and timings read from those reels – in Malayalam, Tamil, Hindi, English and other Indian languages – tell you what to expect before you go. No AI service is needed: everything is read on the phone. Tell the planner your dates, budget, group, transport and vibe, and it suggests where to go.

> The previous Google Maps reviews/photos version is kept on branch `google-map-reviews-and-photos-approach`.

## Features
- **Reels feed** – Explore opens as a vertical swipe feed: each place plays its newest reel (Instagram's official embed) or its photos, with Save, Guide and Details. Toggle to a list any time.
- **Add a reel** – in Instagram tap **Share → TripGuide** (or paste a link, including `instagram.com/share/…` links). The phone reads the reel through Instagram's official embed page (no login, no caption pasting), finds the place (📍 line, hashtag or a known place name) and the state, puts it on the map with Android's geocoder, and extracts prices, timings, phone numbers, distances, months, category, vibes, amenities and tips. **Places exist only from reels** – there is no manual "add place" and no built-in list.
- **Read a video or photo** – pick a saved reel, screen recording, your own clip or a photo (or share it from the Gallery). Offline OCR (Tesseract) reads on-screen text and signboards in English and 10 Indian scripts; Android's speech recognition (Android 13+) transcribes what is said. Without a reel link it creates a new place.
- **Indian languages** – detects Malayalam, Tamil, Telugu, Kannada, Hindi/Marathi, Bengali, Gujarati, Punjabi, Odia and Urdu; keeps the original text with a **Translate** button (Google Translate app or website); search finds places by Malayalam name (മൂന്നാർ → Munnar).
- **Ask my AI (optional)** – sends the caption, extracted text and a few frames to whatever AI app the user has (Gemini, ChatGPT, Claude…) via the share sheet; the user can paste the answer back to fill in more details. No keys, no cost to you.
- **Guide from reels** – each place's Overview combines all its reels on the phone: most repeated tips first, prices, timings and months mentioned, quotes, languages.
- **Guide button** – starts Google Maps turn-by-turn navigation to the place immediately, which carries over to **Android Auto** when the phone is connected.
- **Saved lists** – Favorites, Plan to go, Visited; filter Explore by list.
- **Trip planner** – departure/return time from home, budget, people, transport, vibes → ranked suggestions with cost and travel-time estimates.
- **Filtering** – category chips, vibe chips, must-have amenity checkboxes, hide closed places, sort by name / nearest / cheapest.
- **Trips** – countdown, reminder, packing checklist, budget tracker, notes, share.
- **Moderation** – Report on any reel or community place; 3 reports hide it for everyone. Rules: `docs/community-rules.html`.
- **Settings** – Light / Dark / System theme, dynamic colour, home city or location, currency, km/miles, notifications, data reset.

No built-in places: the directory starts empty and grows from reels. Versions before 1.0.1 shipped 36 built-in places; they are removed on update (trips to them show "Place removed").

## Stack
- **App:** Kotlin · Jetpack Compose (Material 3) · Room · WorkManager · Coil · Firebase (Firestore + Anonymous Auth) · Tesseract OCR.
- **OCR:** Tesseract4Android with tessdata_fast language packs, downloaded on first use (eng, mal, tam, tel, kan, hin, ben, guj, pan, ori, urd).
- **Rules:** `firestore.rules`, with emulator tests in `firebase-tests/`.

## Build & test
```bash
./gradlew testDebugUnitTest      # planner, reels, migration, seed and full UI flow tests (Robolectric)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew recordRoborazziDebug   # regenerate Play Store screenshots
./gradlew bundleRelease          # signed AAB once keystore.properties exists
(cd firebase-tests && npm ci && npm test)            # Firestore rules tests (needs Java)
```

## Community setup (Firebase) – about 15 minutes
Without these values the app still reads reels, videos and photos offline and can add places on the phone; linking reels and sharing places needs Firebase.

**A. Firebase (free Spark plan)**
1. console.firebase.google.com → **Add project** "TripGuide" (Google Analytics off).
2. Build → **Authentication** → Get started → Sign-in method → **Anonymous** → Enable.
3. Build → **Firestore Database** → Create database → production mode → region near India (e.g. `asia-south1`).
4. Project settings → General → **Add app → Android**, package `com.tripguide.app`. Skip google-services.json; copy:
   - **Project ID** → `FIREBASE_PROJECT_ID`
   - **App ID** (`1:…:android:…`) → `FIREBASE_APP_ID`
   - **Web API key** → `FIREBASE_API_KEY`
5. Publish the security rules from the repo root: `npx firebase-tools login`, then `npx firebase-tools deploy --only firestore:rules --project <project id>`.

**B. Put the values in the app build**
- GitHub builds: repo → Settings → Secrets and variables → Actions → add `FIREBASE_PROJECT_ID`, `FIREBASE_APP_ID`, `FIREBASE_API_KEY` (plus the keystore secrets from PLAY_STORE.md for a signed release APK). Re-run the workflow.
- Local builds: add the same three `NAME=value` lines to `local.properties`.

**C. Check:** Settings → Community shows "connected". In Instagram, Share a Kerala reel → TripGuide → Read details → Add reel.

### Limits you should know
- Instagram has **no API for reels by location tag**, and none that gives apps another person's video. TripGuide reads only a reel's public caption (the metadata Meta points developers to), plays reels through Instagram's official embed, and relies on users sharing reels. If Instagram withholds a caption, the user can paste it or add the video.
- Firebase Storage now needs the paid Blaze plan, so shared place photos are stored as small JPEGs inside Firestore (max 3 per place). Move them to Storage when the directory grows.
- Offline extraction is rule-based: it finds facts (prices, times, places, amenities, tips) but doesn't write summaries or translate. Translate opens Google Translate; Ask my AI hands off to the user's AI app.
- OCR reads clear text overlays and signboards well; stylised fonts, tiny text and busy backgrounds read poorly. Speech recognition uses the phone's Google speech service; Malayalam/Tamil support and offline use depend on the phone and the Google app's downloaded languages.
- Anyone can get an anonymous account; add Firebase App Check (Play Integrity) before a large public launch.

## Code map (`app/src/main/java/com/tripguide/app`)
| File | What |
|---|---|
| `Data.kt` | Room entities, DAO, v1→v2 migration, seed loader |
| `Cloud.kt` | Firebase sync of places and reels, reporting, reel caption fetch |
| `Extract.kt` | Offline rules: script/language detection, Malayalam normalisation, prices/times/phones/months/places/tips, reel guide, AI prompt, reel page parsing |
| `Media.kt` | Frames, Tesseract OCR + language packs, speech transcription, Ask my AI and Translate hand-offs |
| `ExploreScreen.kt` | Reels feed / list, search, filters, saved-list filter |
| `PlaceScreen.kt` | Overview with guide from reels, Reels tab, Guide & tips, condition updates |
| `AddReelScreen.kt` | Share/paste a reel or pick media, read details offline, Ask my AI, link to a place or create one |
| `Catalog.kt` | Categories, vibes, amenities, saved lists, packing rules |
| `Planner.kt` | Suggestion scoring and cost/time estimates |
| `App.kt` | Application, notifications, navigation intent, photo import, weather |
| `Components.kt` | Shared UI: Save button, Guide button, reel embed, pickers |
