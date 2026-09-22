# Tag-on/Tag-off Demo — Transit + Campus Access

A sandboxed prototype exploring one phone credential used for two things:
tagging on/off a bus or train, and tapping into campus buildings. It grew
out of a real idea (a phone that works on Auckland Transport *and* gets you
into Manukau Institute of Technology) that turned out to be blocked for a
concrete reason worth restating up front.

## Why this is a sandbox, not the real thing

Real NFC "tap your phone" behaviour — whether for payment, transit, or
building access — is driven by the phone's Secure Element, which Apple
locks to third-party apps entirely and Android exposes only for apps the
user explicitly enables (Host Card Emulation). Building something that
actually taps *your real* AT HOP reader or MIT's door readers would need:

1. A public API to emulate a card on iOS — doesn't exist outside Apple's
   closed Express Transit / campus-badge partner programs, which require
   the transit operator or university to sign an agreement with Apple.
2. The operator's/university's actual cryptographic credentials for your
   account — not something obtainable without them provisioning it.

Neither exists for Auckland Transport (which already supports tap-to-pay
via Apple Pay/bank cards, but not a stored-value AT HOP-equivalent in
Wallet) or MIT (no mobile ID program found) as of this writing. So this
project is two apps we control talking to *each other*, over a protocol we
invented, to demonstrate the mechanics — it will never interoperate with a
real AT or MIT reader.

## Architecture

```
core/   Pure Kotlin (JVM). Fare/tag-on-off state machine, campus access
        rules, and the shared wire protocol (APDU encode/decode). No
        Android or iOS dependency — runs and is unit tested anywhere a JVM
        does, including this sandbox (`cd core && gradle test`).

app/    Android app. The "card": a HostApduService that emulates an NFC
        card using core's logic, plus a small status screen.

ios/    iOS app (source files, not an .xcodeproj). The "reader": a
        CoreNFC-based app that taps the Android card and shows the result,
        playing the role a bus validator or campus door reader would.
```

`core` is genuinely built and tested in this environment (27 tests,
`gradle test` passes). `app` and `ios` are written against the real
Android and CoreNFC/HCE APIs but **not** compiled here — this sandbox has
no Android SDK (Google's Maven repo is unreachable from it) and no
macOS/Xcode. You'll need to build each on its native toolchain.

## Which phone plays which role

- **Android phone**: runs `app`, acts as the card. This is the only side
  that can emulate an NFC card at all (iOS can't, see above).
- **iPhone**: runs the `ios` reader, acts as the validator/door reader.
  Tap it against the Android phone to see the protocol run.

If you only have an iPhone, you can still read the `core` code and see the
logic pass its tests, but you'll need a second, Android device to actually
feel a tap happen.

## Building the Android app

1. Install Android Studio (brings the SDK).
2. Open `app/` as a project — `app/settings.gradle.kts` pulls in `../core`
   automatically via `includeBuild`.
3. Run on a physical NFC-capable Android phone (HCE cannot be tested in
   the emulator).
4. In Settings → NFC → Tap & pay (wording varies by OEM), make sure this
   app is available/selected for the "other" category if the OS asks.

## Building the iOS app

1. In Xcode, create a new iOS App project (SwiftUI, Swift), any bundle ID.
2. Delete the generated `ContentView.swift`/`...App.swift` and add every
   file from `ios/TagDemoReader/` in this repo instead.
3. Add the **Near Field Communication Tag Reading** capability under
   *Signing & Capabilities* (this brings in `TagDemoReader.entitlements`;
   point Xcode at the checked-in one, or let it regenerate and copy the
   `com.apple.developer.nfc.readersession.formats` key over).
4. Add the two keys from `Info-additions.plist` under target → Info
   (Xcode 14+ manages Info.plist from build settings, not a checked-in
   file — see that file's comment for exactly where).
5. Run on a physical iPhone (NFC reading doesn't work in the Simulator).
   Requires a free or paid Apple Developer account to run on-device.

The AID (`F04B414C4953434F5045`) is hardcoded in three places — the Android
`apduservice.xml`, the iOS `Info-additions.plist`, and `Apdu.kt`/
`ApduProtocol.swift` — and must match exactly across all of them, or the
two apps won't recognize each other on tap.

## What the protocol demonstrates

- **Tag on / tag off** with zone-based fares, computed on tag-off once the
  exit stop is known.
- **Daily fare capping**, tracked against a 4am transit-day rollover.
- **Forgot to tag off**: tagging on again while already tagged on
  auto-closes the stale trip (charged at the mode's max fare) unless it's
  an accidental double-tap within a short grace window, which cancels for
  free instead.
- **Campus access rules**: role-based door permissions, opening hours, and
  enrolment status, independent of the transit balance.
- **One "lost/stolen" action locks both** — the transit account and campus
  access share a single credential, so reporting the phone lost blocks
  both subsystems at once (see `UnifiedCredential.kt`).

None of this is tied to Auckland Transport's or MIT's actual fare/access
rules — the fare table and campus doors in `core` are demo data.
