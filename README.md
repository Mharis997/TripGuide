# TripGuide Kerala – Trip Planner (Android)

Tell the app about your trip (dates and times from home, budget, group size, transport, vibes) and it ranks destinations that fit, with cost and travel-time estimates. Every place has photos, a guide, tips, safety info, amenities, best season and live weather.

## Features
- **Smart suggestions** – match score from vibes, budget, available time vs. travel time, season, transport reachability and reported status.
- **Place guides** – photo gallery, how to get there, itinerary, tips, safety & emergency numbers, amenities checklist, 7-day weather (Open-Meteo), directions.
- **Condition updates** – post "Temporarily closed / Road blocked / Weather alert / Crowded / Open / General info" with a photo; status badge updates and upcoming trips to that place get a notification.
- **Add / edit places** – photos, GPS or manual coordinates, category, vibes, amenities, transport, best months, costs, guide text.
- **Explore & filter** – search, category chips, vibe chips, "must-have" amenity checkboxes, hide closed places, favourites, sort by name / nearest / cheapest.
- **Trips** – countdown, departure reminder (WorkManager), auto packing checklist, expense/budget tracker, notes, share itinerary.
- **Google Maps** – *Nearby* tab (top sightseeing within 30 km), *Stays* tab and trip "Where to stay" (hotels with Google rating, photos, price level, map pin, live-price links), *Discover* search for any attraction on Google Maps.
- **Settings** – display name, home city or current location, Light / Dark / System theme buttons, dynamic colour, currency, km/miles, notification toggles and reminder lead time, restore built-in places, delete all data, rate, privacy policy.

Built-in content: 18 Kerala destinations in `app/src/main/assets/places.json` (photos from Wikimedia Commons with credits).

## Stack
Kotlin · Jetpack Compose (Material 3) · Room · Navigation Compose · WorkManager · Coil. Offline-first, no account, no backend.

## Build
```bash
./gradlew testDebugUnitTest      # planner unit tests + full UI flow test (Robolectric)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew recordRoborazziDebug   # regenerate Play Store screenshots
./gradlew bundleRelease          # signed AAB once keystore.properties exists
```

Publishing steps: see [PLAY_STORE.md](PLAY_STORE.md).

## Google Maps setup (attractions, stays, ratings, photos)
Google only issues Places keys on projects with a billing account (card required). Usage is held to $0 by quotas below the free monthly allowance: past the cap Google refuses requests, it does not charge.

**A. Upload key (once):** create it as in PLAY_STORE.md step 1 (`tripguide-upload.jks`). Note its SHA-1:
`keytool -list -v -keystore tripguide-upload.jks -alias upload` → line `SHA1: AA:BB:…`

**B. Google Cloud:**
1. console.cloud.google.com → project picker → **New project** "TripGuide".
2. ☰ → **Billing** → link a billing account. Then Billing → **Budgets & alerts** → Create budget, amount 1, alerts at 50/90/100 %.
3. ☰ → APIs & Services → **Library** → "Places API (New)" → **Enable**.
4. APIs & Services → **Credentials** → Create credentials → **API key** → Edit it:
   - Application restrictions: **Android apps** → Add: package `com.tripguide.app` + SHA-1 from step A. Add a second entry for the **Play App Signing** SHA-1 (Play Console → Test and release → App integrity) once the app is on Play. For a local debug build, also add the SHA-1 shown in the app at Settings → About → Google Maps.
   - API restrictions: **Restrict key** → Places API (New) only. Save.
5. APIs & Services → Places API (New) → **Quotas & system limits**: edit the per-day (or per-minute) limits for SearchNearby, SearchText and GetPhotoMedia so the monthly total stays under the free tier listed on developers.google.com/maps/billing-and-pricing/pricing. Ratings and prices put searches in the Enterprise tier, which has the smallest free allowance, and every result photo counts as its own request.

**C. Put the key in the build:**
- GitHub builds (no PC needed): repo → Settings → Secrets and variables → Actions → New repository secret:
  - `PLACES_API_KEY` = the key
  - `KEYSTORE_BASE64` = output of `base64 -w0 tripguide-upload.jks` (macOS: `base64 -i tripguide-upload.jks`)
  - `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`upload`), `KEY_PASSWORD`
  
  Push or re-run the workflow. Install **`app-release.apk`** from the run's artifacts (it is signed with your upload key, so its SHA-1 matches step B4). The debug APK from CI is signed with a different throwaway key every run, so the Google features do not work in it.
- Local builds: add `PLACES_API_KEY=…` to `local.properties` (git-ignored) and rebuild.

**D. Check:** open the app → Settings → About shows "Google Maps: key installed". Open any place → **Nearby** / **Stays** tabs. A red message there quotes Google's exact reason (wrong SHA-1, API not enabled, quota hit).

Without a key the app still works; Nearby/Stays/Discover say a key is needed, and the Booking.com / Google Hotels live-price buttons always work.

Hotel **prices** are not available from any Google API (free or paid). Stays show Google rating, photos and price level/range where Google has it; the price buttons open Booking.com / Google Hotels pre-filled with trip dates and guests for live prices.

## Code map (`app/src/main/java/com/tripguide/app`)
| File | What |
|---|---|
| `Data.kt` | Room entities, DAO, database, seed loader |
| `Catalog.kt` | Categories, vibes, amenities, transport, packing list rules |
| `Planner.kt` | Suggestion scoring & cost/time estimates (pure Kotlin, unit-tested) |
| `App.kt` | Application, notifications & reminder worker, photo import, weather |
| `Google.kt` | Google Places (New) REST client, parser, Booking.com/Google Hotels price links |
| `DiscoverScreen.kt` | Google result cards, Nearby/Stays section, Discover search |
| `Prefs.kt` | Settings (SharedPreferences, Compose-observable) |
| `*Screen.kt` | UI screens; `Components.kt` shared widgets |
