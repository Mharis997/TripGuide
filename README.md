# TripGuide – Trip Planner (Android)

Tell the app about your trip (dates and times from home, budget, group size, transport, vibes) and it ranks destinations that fit, with cost and travel-time estimates. Every place has photos, a guide, tips, safety info, amenities, best season and live weather.

## Features
- **Smart suggestions** – match score from vibes, budget, available time vs. travel time, season, transport reachability and reported status.
- **Place guides** – photo gallery, how to get there, itinerary, tips, safety & emergency numbers, amenities checklist, 7-day weather (Open-Meteo), directions.
- **Condition updates** – post "Temporarily closed / Road blocked / Weather alert / Crowded / Open / General info" with a photo; status badge updates and upcoming trips to that place get a notification.
- **Add / edit places** – photos, GPS or manual coordinates, category, vibes, amenities, transport, best months, costs, guide text.
- **Explore & filter** – search, category chips, vibe chips, "must-have" amenity checkboxes, hide closed places, favourites, sort by name / nearest / cheapest.
- **Trips** – countdown, departure reminder (WorkManager), auto packing checklist, expense/budget tracker, notes, share itinerary.
- **Settings** – display name, home city or current location, theme (system/light/dark), dynamic colour, currency, km/miles, notification toggles and reminder lead time, restore built-in places, delete all data, rate, privacy policy.

Built-in content: 18 Pakistani destinations in `app/src/main/assets/places.json` (photos from Wikimedia Commons with credits).

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

## Code map (`app/src/main/java/com/tripguide/app`)
| File | What |
|---|---|
| `Data.kt` | Room entities, DAO, database, seed loader |
| `Catalog.kt` | Categories, vibes, amenities, transport, packing list rules |
| `Planner.kt` | Suggestion scoring & cost/time estimates (pure Kotlin, unit-tested) |
| `App.kt` | Application, notifications & reminder worker, photo import, weather |
| `Prefs.kt` | Settings (SharedPreferences, Compose-observable) |
| `*Screen.kt` | UI screens; `Components.kt` shared widgets |
