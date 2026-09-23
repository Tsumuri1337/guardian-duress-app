# Guardian — duress-response app (research prototype)

A stock-Android duress app: on a **user-initiated** duress signal it can alert contacts, capture
evidence, re-seal the app's own data, and — on a provisioned device — factory-reset the phone.
Built as a student + professor research project, tested on a **dedicated** Samsung Galaxy S25 Ultra
(One UI 7 / Android 15).

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
  a phone already signed into Google/Samsung.
- Background audio/photo capture works, but Android 15 shows a **mic/camera privacy indicator**
  that cannot be suppressed. "Covert" means no ordinary UI change — not invisible to the OS.

## 3. Architecture

```
Trigger sources ─┐
  decoy PIN      │
  hardware btns  ├─►  ResponseCoordinator.fire()  ─►  AlertSender     (stub, Phase 2)
  QS tile        │        (single policy point)   ─►  EvidenceCapture (stub, Phase 2)
                 ┘                                 ─►  WipeController  (Device Owner only)
```

| Area | File |
|------|------|
| Response policy (one audit point) | `core/ResponseCoordinator.kt` |
| Feature flags incl. `wipeEnabled` | `core/DuressConfig.kt` |
| Real/duress PIN (salted PBKDF2) | `lock/PinRepository.kt`, `lock/PinLockActivity.kt` |
| Hardware-button listener (screen on/off toggles) | `trigger/TriggerService.kt` |
| Quick Settings tile | `tile/DuressTileService.kt` |
| Factory reset (guarded) | `admin/WipeController.kt`, `admin/DuressDeviceAdminReceiver.kt` |
| Boot persistence | `trigger/BootReceiver.kt` |

## 4. Safety rails built in

- **`DuressConfig.wipeEnabled = false` by default.** The wipe path is inert until you deliberately
  turn it on, so no build can factory-reset the test device by accident.
- **`WipeController.wipe()` fails safe** (returns `false`) whenever the app isn't Device Owner.
- Each response action (`alertEnabled`, `captureEnabled`, `wipeEnabled`) toggles independently so
  you can test them in isolation.

## 5. Build & run

1. Open `C:\ClaudeCode\duress-app` in Android Studio; let it sync (it will fetch Gradle 8.9).
2. Run on the emulator or a normal phone. First launch seeds demo PINs: **real = 1234, duress =
   9999** (shown in a toast). Entering the duress PIN fires the response (visible in Logcat).
3. The hardware-button trigger = **5 power-button presses within 3 s** (screen on/off toggles).

## 6. Enabling the wipe path (dedicated test unit only)

Only on the wiped, account-free S25:

```
adb install app-debug.apk
adb shell dpm set-device-owner com.duress.guardian/.admin.DuressDeviceAdminReceiver
```

Then set `DuressConfig.wipeEnabled = true`, rebuild, and test. **Each successful wipe test factory-
resets the phone**, dropping it back to the empty state — re-provision to test again.

## 7. Status

**Working & tested on emulator:**
- Dual-PIN lock with user-chosen PINs (salted PBKDF2) and a guided setup / change-PINs flow.
- In-app Settings: configurable press count & window, per-trigger toggles (hardware button, QS
  tile), per-response toggles, alert contact + message. Read live at fire-time.
- Trigger sources: decoy PIN, hardware button (screen on/off toggles), QS tile — all funnel through
  `ResponseCoordinator`.
- Covert alert: last-known location (LocationManager) + preset message sent via SMS to the
  configured contact. Runtime permissions requested in Settings.
- Decoy content: separate real / decoy notes vaults. Real vault exposes management controls; the
  decoy vault shows seeded plausible notes and no path to real data or config.
- Guarded factory reset (Device Owner only), default-off.

**Not yet done:**
- `EvidenceCapture` — still a stub (Phase 2).
- Hardening: move PIN hashes / notes to Android Keystore-backed encryption.
- On-device validation: hardware-button trigger timing and Device Owner wipe on the real S25 Ultra.
