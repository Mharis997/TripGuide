# TripGuide Kerala – Reels Trip Directory (Android)

A Kerala travel directory you swipe like reels. Every place shows the Instagram reels travellers shared about it, and an AI guide built from those reels' captions tells you what to expect before you go. Tell the planner your dates, budget, group, transport and vibe, and it suggests where to go.

> The previous Google Maps reviews/photos version is kept on branch `google-map-reviews-and-photos-approach`.

## Features
- **Reels feed** – Explore opens as a vertical swipe feed: each place plays its newest reel (Instagram's official embed) or its photos, with Save, Guide and Details. Toggle to a list any time.
- **Add a reel** – in Instagram tap **Share → TripGuide** (or paste a link). The server reads the reel's public caption and preview image, Claude extracts the place, highlights, tips, costs, timings and how to get there, and the reel is linked to the matching place (or a new place you confirm). Everyone sees it.
- **AI guide from reels** – each place's Overview shows a guide combined from all its reels: summary, highlights, how to spend the visit, tips, best time, costs mentioned, warnings.
- **Guide button** – starts Google Maps turn-by-turn navigation to the place immediately, which carries over to **Android Auto** when the phone is connected.
- **Saved lists** – Favorites, Plan to go, Visited; filter Explore by list.
- **Trip planner** – departure/return time from home, budget, people, transport, vibes → ranked suggestions with cost and travel-time estimates.
- **Add places manually** – photos, coordinates, category, vibes, amenities, transport, best months, costs, guide, tips, safety. Shared with all users.
- **Filtering** – category chips, vibe chips, must-have amenity checkboxes, hide closed places, sort by name / nearest / cheapest.
- **Trips** – countdown, reminder, packing checklist, budget tracker, notes, share.
- **Moderation** – Report on any reel or community place; 3 reports hide it for everyone. Rules: `docs/community-rules.html`.
- **Settings** – Light / Dark / System theme, dynamic colour, home city or location, currency, km/miles, notifications, data reset.

Built-in content: 36 Kerala destinations in `app/src/main/assets/places.json` (photos from Wikimedia Commons with credits). Works offline without the community backend; reels and sharing need it.

## Stack
- **App:** Kotlin · Jetpack Compose (Material 3) · Room · WorkManager · Coil · Firebase (Firestore + Anonymous Auth).
- **Server:** `server/` – Cloudflare Worker (TypeScript, Anthropic SDK): verifies the Firebase sign-in, reads reel previews, calls Claude with structured outputs.
- **Rules:** `firestore.rules`, with emulator tests in `firebase-tests/`.

## Build & test
```bash
./gradlew testDebugUnitTest      # planner, reels, migration, seed and full UI flow tests (Robolectric)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew recordRoborazziDebug   # regenerate Play Store screenshots
./gradlew bundleRelease          # signed AAB once keystore.properties exists
(cd server && npm ci && npm test && npm run check)   # server tests + typecheck
(cd firebase-tests && npm ci && npm test)            # Firestore rules tests (needs Java)
```

## Community setup (Firebase + AI server) – about 30 minutes
Without these values the app runs as an offline directory, and the Reels tabs say setup is missing.

**A. Firebase (free Spark plan)**
1. console.firebase.google.com → **Add project** "TripGuide" (Google Analytics off).
2. Build → **Authentication** → Get started → Sign-in method → **Anonymous** → Enable.
3. Build → **Firestore Database** → Create database → production mode → region near India (e.g. `asia-south1`).
4. Project settings → General → **Add app → Android**, package `com.tripguide.app`. Skip google-services.json; copy:
   - **Project ID** → `FIREBASE_PROJECT_ID`
   - **App ID** (`1:…:android:…`) → `FIREBASE_APP_ID`
   - **Web API key** → `FIREBASE_API_KEY`
5. Publish the security rules from the repo root: `npx firebase-tools login`, then `npx firebase-tools deploy --only firestore:rules --project <project id>`.

**B. Claude API key**
1. console.anthropic.com → API keys → Create key.
2. Billing → set a **monthly spend limit**. That is your hard cap if anyone abuses the server.
3. Default model is `claude-opus-5-5` at low effort, a few cents per reel read and per guide rebuild. For roughly 1/40th of that cost, set `CLAUDE_MODEL = "claude-haiku-5-5"` in `server/wrangler.toml`.

**C. AI server (Cloudflare Workers free plan)**
```bash
cd server && npm ci
npx wrangler login
# put your Firebase project ID in wrangler.toml (FIREBASE_PROJECT_ID), then:
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler deploy            # prints https://tripguide-api.<you>.workers.dev
```
That URL is `TRIPGUIDE_API_URL`.

**D. Put the values in the app build**
- GitHub builds: repo → Settings → Secrets and variables → Actions → add `FIREBASE_PROJECT_ID`, `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `TRIPGUIDE_API_URL` (plus the keystore secrets from PLAY_STORE.md for a signed release APK). Re-run the workflow.
- Local builds: add the same four `NAME=value` lines to `local.properties`.

**E. Check:** Settings → Community shows "connected · AI reel reader on". In Instagram, Share a Kerala reel → TripGuide → Read reel → Add reel.

### Limits you should know
- Instagram has **no API for reels by location tag**, and none that gives apps another person's video. TripGuide reads only a reel's public caption and preview image (the metadata Meta points developers to), plays reels through Instagram's official embed, and relies on users sharing reels. If Instagram withholds a caption (private, removed or rate-limited reel), the reel can't be added.
- Firebase Storage now needs the paid Blaze plan, so shared place photos are stored as small JPEGs inside Firestore (max 3 per place). Move them to Storage when the directory grows.
- Anyone can get an anonymous account, so the server's protection is the sign-in check plus your Anthropic spend limit. Add Firebase App Check (Play Integrity) before a large public launch.

## Code map (`app/src/main/java/com/tripguide/app`)
| File | What |
|---|---|
| `Data.kt` | Room entities, DAO, v1→v2 migration, seed loader |
| `Cloud.kt` | Firebase sync of places/reels/summaries, reporting, AI server calls, reel link helpers |
| `ExploreScreen.kt` | Reels feed / list, search, filters, saved-list filter |
| `PlaceScreen.kt` | Overview with AI reel guide, Reels tab, Guide & tips, condition updates |
| `AddReelScreen.kt` | Share/paste a reel, AI read, link to a place or create one |
| `Catalog.kt` | Categories, vibes, amenities, saved lists, packing rules |
| `Planner.kt` | Suggestion scoring and cost/time estimates |
| `App.kt` | Application, notifications, navigation intent, photo import, weather |
| `Components.kt` | Shared UI: Save button, Guide button, reel embed, pickers |
