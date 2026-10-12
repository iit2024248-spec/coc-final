# CoC Auto Attack

Requires Android 11+ (API 30).

## Build the APK
1. Install Android Studio. File > Open > select this `CocAutoAttack` folder. Wait for Gradle sync (needs internet the first time).
2. Build > Build Bundle(s) / APK(s) > Build APK(s).
3. Click "locate" in the popup, or find `app/build/outputs/apk/debug/app-debug.apk`.
4. Copy it to your phone and install it (allow "install unknown apps").

## First run
1. Open the app > "Enable accessibility service" > find "CoC Auto Attack" > turn on.
   If it is greyed out (Android 13+): Settings > Apps > CoC Auto Attack > top-right menu (three dots) > "Allow restricted settings", then try again.
2. Set your minimum gold/elixir, then press "Save settings & show floating icon".
3. Open Clash of Clans (landscape). Tap the floating icon > Calibrate. For each step, move the game to the screen named, press Snap, then tap the spots it asks for on the frozen screenshot. This is done once per phone.
4. Tap the icon > Start. Tap the icon > Stop to end.

## Tuning
- Troops not dropping: tap the deploy corners a bit further outside the red zone when calibrating.
- Loot misread (status shows "can't read loot"): recalibrate the loot boxes, tight around only the digits.
- Scans too slow or reading the previous base: change "Wait after Next".

## Build without Android Studio (GitHub)
1. Make a free account at github.com > New repository (any name).
2. "uploading an existing file": upload everything inside the unzipped folder, including the hidden `.github` folder. (If your browser can't upload folders, use github.dev or GitHub Desktop.)
3. Open the Actions tab > "Build APK" > wait ~5 min for the green tick.
4. Open that run > Artifacts > download CocAutoAttack-apk (a zip with app-debug.apk inside).
