# MessageFilter — Who Called SMS shield (iOS)

iOS Message Filter Extension (`ILMessageFilterExtension`). When the user enables
"Filtrer les expéditeurs inconnus" → Who Called in **Settings > Apps > Messages**,
iOS routes SMS from unknown senders here. We decide **offline** from the same
shared list as the call blocker and send `block` senders to the Junk folder.

Privacy: 100% offline — we never inspect message content and do **not** set
`ILMessageFilterExtensionNetworkURL` (no network query).

## Files

- `MessageFilterExtension.swift` — extension entry point (`ILMessageFilterQueryHandling`).
- `MessageFilterService.swift` — offline matching: reads `scored_numbers.json`
  from the App Group and returns `.junk` for blocked senders.
- `Info.plist` — `NSExtensionPointIdentifier = com.apple.identitylookup.message-filter`.
- `MessageFilter.entitlements` — App Group `group.com.whocalled.app`.

## Adding the target in Xcode (one-time, manual — no .xcodeproj is versioned)

1. **File > New > Target… > Message Filter Extension**. Name it `MessageFilter`.
   Bundle id e.g. `com.whocalled.app.messagefilter`. Embed in the `WhoCalled` app.
2. Delete the auto-generated sources and **add the files in this folder** to the
   new target (`MessageFilterExtension.swift`, `MessageFilterService.swift`),
   and set this folder's `Info.plist` / `MessageFilter.entitlements` as the
   target's Info.plist and Code Signing Entitlements.
3. **Signing & Capabilities** for the `MessageFilter` target → add **App Groups**
   and check `group.com.whocalled.app` (same as the app + Call Directory).
4. Build & run. On device: **Settings > Apps > Messages > Expéditeurs inconnus et
   indésirables > Filtrer les expéditeurs inconnus**, then pick **Who Called**.

The in-app entry point is the green "Filtre SMS" card on the Home screen
(`SmsFilterSetupView`), which explains these steps and links to Settings.
