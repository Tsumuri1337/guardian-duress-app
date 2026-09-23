# Guardian — duress-response app (research prototype)

A stock-Android duress app: on a **user-initiated** duress signal it can alert contacts, capture
evidence, re-seal the app's own data, and — on a provisioned device — factory-reset the phone. It
can also wipe on prolonged inactivity (a dead-man's switch). Built as a research project, validated
on a **dedicated** OnePlus 10 Pro (OxygenOS 15 / Android 15).

**Project direction (hybrid):** *Track 1* is this stock, deployable app (what a normal user could
install). *Track 2* (planned) is a rooted **LSPosed** module that hooks the real system lockscreen
to detect a duress PIN and broadcast to this app's `ResponseCoordinator` — lab-only, on the rooted
OnePlus, since it can't ship to end users.

---

## 1. Threat model & intended use (read first)

This section exists so the project stays on the defensive-security side of the line, and because
the same capabilities, pointed at someone else's phone without their knowledge, would be
stalkerware. State this explicitly in any write-up.

- **Who the user is:** the *owner* of the device, who installs and configures the app on *their own*
  phone and sets their own PINs / trigger.
- **Whose device it is:** the user's own phone (here, a dedicated test unit that stays wiped and is
  never signed into a personal account).
- **What consent looks like:** the person triggering the response is the person the response
  protects. There is no covert deployment onto a third party's device — provisioning as Device
  Owner requires physical access and a factory reset, and cannot be pushed silently.
- **Threat being defended against:** coercion — someone forcing the user to unlock/hand over the
  phone. The response protects data and/or summons help at the user's own command.

## 2. What is / isn't possible on stock, unrooted Android

- An app **cannot** intercept the real system-lockscreen PIN. The duress PIN lives on this app's
  **own** lock screen (`PinLockActivity`).
- Factory reset (`DevicePolicyManager.wipeData`) requires the app to be a **Device Owner**,
  provisioned via ADB on a freshly reset device with **no accounts** added. It cannot be granted on
  a phone already signed into a Google/OEM account.
- Background audio/photo capture works, but Android 15 shows a **mic/camera privacy indicator**
  that cannot be suppressed. "Covert" means no ordinary UI change — not invisible to the OS.

## 3. Architecture

```
Trigger sources ─┐
  decoy PIN      │
  hardware btns  ├─►  ResponseCoordinator.fire()  ─►  AlertSender     (SMS + location)
  QS tile        │        (single policy point)   ─►  EvidenceCapture (stub, Phase 2)
                 ┘                                 ─►  WipeController  (Device Owner only)

  inactivity timer ─►  InactivityWatchdog ─(grace + warning)─►  WipeController
```

| Area | File |
|------|------|
| Response policy (one audit point) | `core/ResponseCoordinator.kt` |
| Settings/defaults (live, user-editable) | `core/SettingsRepository.kt`, `core/DuressConfig.kt`, `core/SettingsActivity.kt` |
| Real/duress PIN (salted PBKDF2) + setup | `lock/PinRepository.kt`, `lock/PinLockActivity.kt`, `lock/PinSetupActivity.kt` |
| Hardware-button listener (screen on/off toggles) | `trigger/TriggerService.kt` |
| Quick Settings tile | `tile/DuressTileService.kt` |
| Covert alert (SMS + location) | `response/AlertSender.kt` |
| Real/decoy notes vaults | `vault/VaultActivity.kt`, `vault/NotesRepository.kt` |
| Inactivity dead-man's switch | `watchdog/InactivityWatchdog.kt`, `watchdog/InactivityPolicy.kt` |
| Factory reset (guarded) | `admin/WipeController.kt`, `admin/DuressDeviceAdminReceiver.kt` |
| Boot persistence | `trigger/BootReceiver.kt` |

## 4. Safety rails built in

- **`DuressConfig.wipeEnabled = false` by default.** The wipe path is inert until you deliberately
  turn it on, so no build can factory-reset the test device by accident.
- **`WipeController.wipe()` fails safe** (returns `false`) whenever the app isn't Device Owner.
- Each response action (`alertEnabled`, `captureEnabled`, `wipeEnabled`) toggles independently so
  you can test them in isolation.
- **Inactivity auto-wipe** is off by default, has its own toggle, is Device-Owner-gated, and only
  fires after a **grace window with a visible warning** — any unlock aborts it, and it re-arms on
  boot. The wipe additionally passes `WIPE_RESET_PROTECTION_DATA` so a test wipe leaves the device
  re-provisionable (no Factory Reset Protection lock).

## 5. Build & run

1. Open `duress-app` in Android Studio; let it sync (it will fetch Gradle 8.9).
2. Run on the emulator or a phone. First launch shows a **setup screen** where you choose your own
   real and duress PINs (4–12 digits). Entering the duress PIN opens the decoy vault and fires the
   response (visible in Logcat, tag `ResponseCoordinator`).
3. The hardware-button trigger defaults to **6 power-button presses within 3 s** (screen on/off
   toggles; 6 avoids the OS "5 presses = Emergency SOS" gesture). Tune it, and everything else, in
   the in-app **Settings**.

## 6. Enabling the wipe path (dedicated test unit only)

Only on the wiped, account-free test unit (the OnePlus):

```
adb install app-debug.apk
adb shell dpm set-device-owner com.duress.guardian/.admin.DuressDeviceAdminReceiver
```

Then enable the wipe in **Settings** (or the inactivity auto-wipe) and test. **Each successful wipe
test factory-resets the phone**, dropping it back to the empty state — re-provision to test again.

## 7. Status

**Working:**
- Dual-PIN lock with user-chosen PINs (salted PBKDF2) and a guided setup / change-PINs flow.
- In-app Settings: configurable press count & window, per-trigger toggles, per-response toggles,
  alert contact + message, inactivity threshold. Read live at fire-time.
- Trigger sources: decoy PIN, hardware button, QS tile — all funnel through `ResponseCoordinator`.
- Covert alert: last-known location (LocationManager) + preset message sent via SMS. Runtime
  permissions requested in Settings.
- Decoy content: separate real / decoy notes vaults.
- Inactivity auto-wipe (dead-man's switch) with grace + warning; decision logic unit-tested.
- Guarded factory reset (Device Owner only), default-off.

**Validated on-device (OnePlus 10 Pro, OxygenOS 15 / Android 15):**
- All three triggers fire (decoy PIN, 6× power button, QS tile).
- Covert alert sends over SMS with location; fail-safe when no contact set.
- Inactivity cycle (arm → warn → grace → wipe request) verified in-log (wipe no-op without Device
  Owner).

**Not yet done:**
- `EvidenceCapture` — still a stub (Phase 2).
- Hardening: move PIN hashes / notes to Android Keystore-backed encryption.
- On-device: reboot persistence under OxygenOS battery management; Device Owner factory-reset.
- Track 2: the rooted LSPosed lockscreen-duress-PIN module.
