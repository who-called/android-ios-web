# Who Called — Android

Real-time call blocking with **CallScreeningService** + crowdsourced scoring.
Kotlin · Jetpack Compose · Room · WorkManager · DataStore.

> Proprietary code, original implementation. Architecture inspired by Saracroche
> (public Android APIs) but **no GPL code copied**.

## SDK targets

- **minSdk 29** (Android 10) — `CallScreeningService` + `RoleManager.ROLE_CALL_SCREENING`
  require API 29; below that, call screening isn't available. Covers ~95%+ of devices.
- **targetSdk / compileSdk 37** — keep on the latest stable when shipping (Play requires
  a recent target API). The repo currently uses 37 (matches the installed SDK).

## Release config

- HTTP cleartext is **debug-only** (`src/debug/AndroidManifest.xml` → `10.0.2.2`).
  Release is **HTTPS-only** (`API_BASE_URL` → prod).
- Debug seed data is gated by `BuildConfig.DEBUG` (no mock data in release).
- `READ_CALL_LOG` is **optional**: the app works fully without it (the "report from
  recent calls" list simply shows an "Autoriser" card). Request is runtime + explained.
- Build the store artifact: `./gradlew bundleRelease` (AAB).

## Signing (release)

The release build is signed from secrets in `android/keystore.properties`
(gitignored). To set it up on your machine:

1. **Generate your keystore** (keep the `.jks` safe FOREVER — losing it means you
   can never publish an update to the same app):
   ```bash
   keytool -genkeypair -v -keystore keystore/whocalled-release.jks \
     -alias whocalled -keyalg RSA -keysize 2048 -validity 10000
   ```
2. `cp keystore.properties.example keystore.properties` and fill in your passwords.
3. `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.

When you upload to Play, also enable **Play App Signing** (Google manages the final
signing key; your upload key is the one above). Never commit `keystore.properties`
or any `.jks` (already in `.gitignore`).

## Run

Open in Android Studio (provides JDK + Android SDK), then:

```bash
./gradlew assembleDebug      # build debug APK
./gradlew test               # JVM unit tests (screening logic, normalization)
```

Debug builds point at `http://10.0.2.2:3000/api/v1` (the local backend from the
emulator). Release uses `https://api.who-called.com/api/v1`. See `app/build.gradle.kts`.

## How it works

- `WhoCalledScreeningService` (`BIND_SCREENING_SERVICE`) decides per call:
  - **BLOCK** → `setDisallowCall(true).setRejectCall(true)` (silent)
  - **WARN** → ring **+** high-priority notification with the spam score
    (the "j'ai un doute" level — signature feature)
  - **ALLOW** → ring normally
- Decision (`ScreeningDecision`) reads the local **Room** cache (`scored_numbers`)
  and user overrides (`user_rules`: allow/block). Pure + unit-tested.
- `ListSyncWorker` delta-syncs the scored list every 6h (warm-up = full list on
  first launch). Cache lives in Room — this is the app's "NoSQL cache".
- Reports are **100% anonymous**: only a random `deviceId` UUID is sent, no PII.
- Numbers normalized to E.164 without `+` (FR-first, `0X…`→`33X…`).

## i18n

UI strings live in `res/values/strings.xml` (FR). Add `values-xx/strings.xml`
to translate. The API carries a `locale` field on reports.
