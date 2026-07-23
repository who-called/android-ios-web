# who-called — iOS

Call blocking + identification via **CallKit Call Directory** + crowdsourced scoring.
SwiftUI · MVVM · App Group shared storage. iOS 15+.

> Proprietary, original code. Architecture inspired by Saracroche (public CallKit
> APIs + App Group pattern) but **no GPL code copied**.

## iOS limitation (vs Android) — important

iOS does **not** wake the extension per call and offers **no custom in-call alert**.
So the WARN level ("j'ai un doute") is implemented as a CallKit **identification
label** ("⚠️ who-called · spam X%") shown under the number while it rings — the
maximum iOS allows. BLOCK numbers are rejected silently. This matches Truecaller/Hiya/Saracroche.

## Targets

| Target | Role |
|--------|------|
| `WhoCalled` (app) | SwiftUI UI, syncs the list, writes it to the App Group, reloads the extension |
| `CallDirectory` (extension) | Reads the prepared list and registers block + identify entries |
| `ShareExtension` (extension) | Share-sheet entry: extract a number/SMS shared from any app and open the host app pre-filled on the Report tab (`whocalled://report?phone=…`) |

Both share the `WhoCalled/Shared/` files (`AppConstants`, `ScoredNumber`,
`PhoneNormalizer`, `SharedStore`, `ReportCategory`, `MyReport`, `PatternExpander`,
`DebugSeeder`) — add `AppConstants`, `ScoredNumber`, `PhoneNormalizer`, `SharedStore`
to **both** targets' membership (the extension reads them).

### ARCEP patterns (wildcards)
The backend serves wildcard patterns (e.g. `33899######`). Since iOS can't match
wildcards at call time, `PatternExpander` expands them into individual numbers at
sync time (`ListService`), capped at 1M entries for Call Directory performance.
Expanded entries are tagged `source = "arcep"`.

### UI files (app target only)
`App.swift`, `Views/` (`HomeView`, `ReportView`, `MyReportsView`, `NumberDetailView`,
`SettingsView`, `Components.swift`), `ViewModels/MainViewModel.swift`, `Services/`.

### Public links (the "/.env")
All public URLs live in `AppConstants.Links` (site / privacy / policy / repo /
rate / contact email) — change them in one place. Mirrors Android `BuildConfig`.

### Design
Clean white surfaces, hairline borders (`BorderedCard`), no heavy shadows, modest
radii. Signature `ScoreGauge` + `StatusBadge` + tinted `FeedbackBanner`.

## Data flow

1. App calls `ListService.update()` → `GET /lists` (warm-up full, then delta `?since=`).
2. Numbers written as JSON to the App Group container (`scored_numbers.json`),
   **sorted ascending** (CallKit requirement).
3. `CXCallDirectoryManager.reloadExtension()` wakes the extension.
4. `CallDirectoryHandler` reads the file and adds blocking/identification entries.
5. Reports go to `POST /reports` — **100% anonymous** (`deviceId` UUID only, no PII).

## Wiring the Xcode project (no .xcodeproj committed yet)

Create in Xcode:
1. **App target** `WhoCalled` (SwiftUI). Add `App.swift`, `Views/`, `ViewModels/`,
   `Services/`, `Shared/`, `Info.plist`, `WhoCalled.entitlements`.
   - **Warm-up resource**: add `Resources/warmup_ios.json` to the app target's
     *Copy Bundle Resources*. It's the embedded FR spam list `WarmupLoader` loads
     into the App Group on first launch (offline-ready). Generated via
     `dbforge snapshot --prefix 33` (gitignored — not source). The Call Directory
     extension does **not** need it (it reads the App Group file the app writes).
2. **Call Directory Extension** target `CallDirectory` (bundle id
   `com.whocalled.app.calldirectory`). Add `CallDirectory/CallDirectoryHandler.swift`,
   `Info.plist`, `CallDirectory.entitlements`, **and** the `Shared/` files (shared membership).
3. **Share Extension** target `ShareExtension` (bundle id
   `com.whocalled.app.shareextension`). Add `ShareExtension/ShareViewController.swift`,
   `ShareExtension/Info.plist`, `ShareExtension/ShareExtension.entitlements`, **and**
   the `Shared/` files it needs (`PhoneNormalizer`, `AppConstants` — shared membership).
   It hands the number to the app via the custom URL scheme `whocalled://` (registered
   in the app's `Info.plist` under `CFBundleURLTypes`).
4. Enable **App Groups** capability `group.com.whocalled.app` on all extension targets.
5. Set `apiBaseURL` in `AppConstants.swift` (use your local backend during dev).

### Features beyond the block list (parity with web)
- **Tendances** tab (`Views/TrendingView.swift`): `GET /trending` — numbers ramping
  up now, with the dominant community reason. Tap → pre-fills the Report tab.
- **Motif communautaire**: `NumberDetailView` shows "Le plus souvent : démarchage (X %)"
  from `lookup.topReason`.
- **Signalement par partage**: the Share Extension above (no SMS permission).
- **Empty states**: `EmptyState` component (`Views/Components.swift`) covers empty
  watched-numbers, reports, and trends lists.

`./gradlew`-style build isn't applicable; build via Xcode or `xcodebuild`.
Unit tests in `WhoCalledTests/` cover phone normalization + model.
```
