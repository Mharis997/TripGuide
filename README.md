# TripGuide – Trip Planner (Android)

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

## Google Maps setup (attractions, stays, ratings, photos) – $0 configuration
Google requires a billing account on every Places key; there is no key-free route. You stay at $0 by capping usage below the free monthly allowance, so Google refuses requests instead of charging.

1. console.cloud.google.com → new project → enable **Places API (New)**.
2. Billing → link a billing account (card required). Billing → Budgets & alerts → budget of 1 (any currency) with email alerts.
3. APIs & Services → Places API (New) → **Quotas**: set the per-day limits for *Search Nearby*, *Search Text* and *Get Photo Media* requests to **30/day** each (≈900/month, under the 1,000/month free tier for the Enterprise-level fields this app uses – ratings and prices). Check current free caps at developers.google.com/maps/billing-and-pricing/pricing.
4. Credentials → Create API key → **Application restriction: Android apps** → add package `com.tripguide.app` with the SHA-1 of your debug key, your upload key and the Play App Signing key (Play Console → Test and release → App integrity). **API restriction:** Places API (New) only.
5. Add to `local.properties` (git-ignored): `PLACES_API_KEY=your_key` and rebuild. For CI, set a `PLACES_API_KEY` repository secret/env var.

Without a key the app works fully offline; the Nearby/Stays/Discover sections say a key is needed, and the Booking.com / Google Hotels live-price buttons still work.

Hotel **prices** are not available from any Google API (free or paid). The Stays lists show Google rating, photos and price level/range where Google has it, and the price buttons open Booking.com / Google Hotels pre-filled with the trip dates and group size, which show live prices.

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
